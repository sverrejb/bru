use iroh::{
    Endpoint, EndpointAddr, EndpointId, RelayMap, RelayUrl, SecretKey, Watcher,
    endpoint::{Connection, RecvStream, RelayMode, SendStream, presets},
};
use js_sys::{Function, Promise, Uint8Array};
use n0_future::time::{Duration, timeout};
use qrcode::{QrCode, render::svg};
use std::{error::Error, str::FromStr};
use wasm_bindgen::{JsCast, prelude::*};
use wasm_bindgen_futures::JsFuture;

const ALPN: &[u8] = b"bru/1";
const MAX_RESPONSE: usize = 16 * 1024 * 1024;
const MAX_PUSH: usize = 8 * 1024 * 1024;
const CHUNK: usize = 64 * 1024;
const STALL: Duration = Duration::from_secs(30);
const LOG_LIMIT: usize = 200;
const PAIRING_URL: &str = "https://bru.works/pair";

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = console, js_name = log)]
    fn console_log(s: &str);
}

// truncate log so that we do not log out the whole payload
fn log(s: &str) {
    console_log(&s.chars().take(LOG_LIMIT).collect::<String>());
}

// This escapes only what JSON requires and leaves everything else as raw UTF-8, which JSON strings allow unescaped.
fn json_string(s: &str) -> String {
    let mut out = String::with_capacity(s.len() + 2);
    out.push('"');
    for c in s.chars() {
        match c {
            '"' => out.push_str("\\\""),
            '\\' => out.push_str("\\\\"),
            '\n' => out.push_str("\\n"),
            '\r' => out.push_str("\\r"),
            '\t' => out.push_str("\\t"),
            c if (c as u32) < 0x20 => out.push_str(&format!("\\u{:04x}", c as u32)),
            c => out.push(c),
        }
    }
    out.push('"');
    out
}

fn is_base64(b: u8) -> bool {
    b.is_ascii_alphanumeric() || matches!(b, b'+' | b'/' | b'=')
}

fn js_error(e: JsValue) -> JsError {
    JsError::new(&e.as_string().unwrap_or_else(|| format!("{e:?}")))
}

async fn read_header(recv: &mut RecvStream) -> Result<(Vec<u8>, Vec<u8>), Box<dyn Error>> {
    let mut header = Vec::new();
    while let Some(chunk) = recv.read_chunk(CHUNK).await? {
        if let Some(i) = chunk.iter().position(|&b| b == b'\n') {
            header.extend_from_slice(&chunk[..i]);
            return Ok((header, chunk[i + 1..].to_vec()));
        }
        header.extend_from_slice(&chunk);
        if header.len() > MAX_PUSH {
            return Err("message too large".into());
        }
    }
    Ok((header, Vec::new()))
}

fn deliver(on_chunk: &Function, bytes: &[u8]) -> Result<(), Box<dyn Error>> {
    on_chunk
        .call1(&JsValue::NULL, &Uint8Array::from(bytes))
        .map(|_| ())
        .map_err(|_| "chunk handler failed".into())
}

async fn pump(send: &mut SendStream, next: &Function) -> Result<(), JsError> {
    loop {
        let pending = next.call0(&JsValue::NULL).map_err(js_error)?;
        let chunk = JsFuture::from(Promise::resolve(&pending)).await.map_err(js_error)?;
        if chunk.is_null() {
            return Ok(());
        }
        let bytes = chunk
            .dyn_into::<Uint8Array>()
            .map_err(|_| JsError::new("file chunks must be bytes"))?;
        timeout(STALL, send.write_all(&bytes.to_vec()))
            .await
            .map_err(|_| JsError::new("Timed out reaching phone"))??;
    }
}

async fn exchange(conn: &Connection, req: &[u8], body: Option<&Function>) -> Result<Vec<u8>, JsError> {
    let (mut send, mut recv) = conn.open_bi().await?;
    send.write_all(req).await?;
    if let Some(next) = body {
        send.write_all(b"\n").await?;
        pump(&mut send, next).await?;
    }
    send.finish()?;
    Ok(recv.read_to_end(MAX_RESPONSE).await?)
}

async fn receive(conn: &Connection, on_chunk: &Function) -> Result<String, Box<dyn Error>> {
    let (mut send, mut recv) = conn.accept_bi().await?;
    let (header, mut batch) = read_header(&mut recv).await?;
    let message = String::from_utf8_lossy(&header).into_owned();
    log(&format!("[bru] accept_incoming: message={message}"));
    while let Some(chunk) = recv.read_chunk(CHUNK).await? {
        batch.extend_from_slice(&chunk);
        if batch.len() >= CHUNK {
            deliver(on_chunk, &batch)?;
            batch.clear();
        }
    }
    if !batch.is_empty() {
        deliver(on_chunk, &batch)?;
    }
    send.write_all(br#"{"ok":true}"#).await?;
    send.finish()?;
    conn.closed().await;
    Ok(message)
}

#[wasm_bindgen]
pub struct Bru {
    endpoint: Endpoint,
    relay_url: Option<String>,
}

#[wasm_bindgen]
impl Bru {
    pub async fn open(
        secret_key: &[u8],
        relay_url: Option<String>,
        relay_token: Option<String>,
    ) -> Result<Bru, JsError> {
        let key: [u8; 32] = secret_key.try_into()?;
        let mut builder = match &relay_url {
            Some(_) => Endpoint::builder(presets::Minimal),
            None => Endpoint::builder(presets::N0),
        }
        .secret_key(SecretKey::from_bytes(&key))
        .alpns(vec![ALPN.to_vec()]);
        if let Some(url) = &relay_url {
            let map = RelayMap::try_from_iter([url.as_str()])?;
            builder = builder.relay_mode(RelayMode::Custom(match relay_token {
                Some(token) => map.with_auth_token(token),
                None => map,
            }));
        }
        let endpoint = builder.bind().await?;
        log(&format!(
            "[bru] endpoint bound, id={} relay={}",
            endpoint.id(),
            relay_url.as_deref().unwrap_or("n0")
        ));
        Ok(Bru {
            endpoint,
            relay_url,
        })
    }

    pub fn id(&self) -> String {
        self.endpoint.id().to_string()
    }

    pub async fn online(&self) -> Result<(), JsError> {
        let mut watcher = self.endpoint.home_relay_status();
        loop {
            for status in watcher.get() {
                if status.is_connected() {
                    return Ok(());
                }
                if let Some(reason) = status.auth_denied_reason() {
                    return Err(JsError::new(&format!(
                        "The relay at {} rejected this client: {reason}. Check the access token.",
                        status.url()
                    )));
                }
            }
            watcher
                .updated()
                .await
                .map_err(|_| JsError::new("endpoint closed"))?;
        }
    }

    pub async fn close(&self) {
        self.endpoint.close().await;
    }

    pub fn relay_error(&self) -> String {
        let target = self.relay_url.as_deref().unwrap_or("a relay server");
        match self
            .endpoint
            .home_relay_status()
            .get()
            .into_iter()
            .find_map(|status| status.last_error().map(|err| format!("{err:#}")))
        {
            Some(detail) => format!("Could not reach {target}: {detail}"),
            None => format!("Could not reach {target}. Check your connection."),
        }
    }

    pub fn pair_url(&self, name: &str) -> String {
        format!("{PAIRING_URL}#d={}&n={name}", self.id())
    }

    pub fn pairing_code(&self, name: &str) -> Result<String, JsError> {
        let code = QrCode::new(self.pair_url(name))?;
        Ok(code
            .render::<svg::Color>()
            .min_dimensions(512, 512)
            .light_color(svg::Color("#0000"))
            .quiet_zone(false)
            .build())
    }

    pub async fn accept_incoming(
        &self,
        phone_id: Option<String>,
        on_chunk: Function,
    ) -> Result<String, JsError> {
        log("[bru] accept_incoming: waiting for a connection");
        let incoming = self
            .endpoint
            .accept()
            .await
            .ok_or_else(|| JsError::new("endpoint closed"))?;
        let conn = match incoming.await {
            Ok(conn) => conn,
            Err(e) => return Ok(format!(r#"{{"error":{}}}"#, json_string(&e.to_string()))),
        };
        let remote = conn.remote_id().to_string();
        log(&format!("[bru] accept_incoming: connection from {remote}"));
        if phone_id.is_some_and(|id| id != remote) {
            conn.close(1u32.into(), b"unknown peer");
            return Ok(format!(r#"{{"id":"{remote}","error":"unknown peer"}}"#));
        }
        Ok(match receive(&conn, &on_chunk).await {
            Ok(message) => format!(r#"{{"id":"{remote}","message":{message}}}"#),
            Err(e) => format!(r#"{{"id":"{remote}","error":{}}}"#, json_string(&e.to_string())),
        })
    }

    pub async fn health(&self, phone_id: &str) -> Result<String, JsError> {
        self.request(phone_id, br#"{"op":"health"}"#, None).await
    }

    pub async fn messages(&self, phone_id: &str, since: u32, limit: u32) -> Result<String, JsError> {
        let req = format!(r#"{{"op":"messages","since":{since},"limit":{limit}}}"#);
        self.request(phone_id, req.as_bytes(), None).await
    }

    pub async fn send_message(
        &self,
        phone_id: &str,
        to: &str,
        body: &str,
        client_id: &str,
    ) -> Result<String, JsError> {
        let req = format!(
            r#"{{"op":"send","to":{},"body":{},"clientId":{}}}"#,
            json_string(to),
            json_string(body),
            json_string(client_id),
        );
        self.request(phone_id, req.as_bytes(), None).await
    }

    pub async fn send_clipboard(
        &self,
        phone_id: &str,
        text: &str,
        mime: Option<String>,
        data: Option<String>,
    ) -> Result<String, JsError> {
        let mut req = format!(r#"{{"op":"clipboard","text":{}"#, json_string(text));
        if let (Some(mime), Some(data)) = (mime, data) {
            if !data.bytes().all(is_base64) {
                return Err(JsError::new("image data must be base64"));
            }
            req.reserve(data.len() + 32);
            req.push_str(r#","mime":"#);
            req.push_str(&json_string(&mime));
            req.push_str(r#","data":""#);
            req.push_str(&data);
            req.push('"');
        }
        req.push('}');
        self.request(phone_id, req.as_bytes(), None).await
    }

    pub async fn send_file(
        &self,
        phone_id: &str,
        name: &str,
        mime: &str,
        next: Function,
    ) -> Result<String, JsError> {
        let header = format!(
            r#"{{"op":"file","name":{},"mime":{}}}"#,
            json_string(name),
            json_string(mime),
        );
        self.request(phone_id, header.as_bytes(), Some(&next)).await
    }
}

impl Bru {
    async fn request(
        &self,
        phone_id: &str,
        req: &[u8],
        body: Option<&Function>,
    ) -> Result<String, JsError> {
        log(&format!(
            "[bru] request to {phone_id}: {}",
            String::from_utf8_lossy(req)
        ));
        let id = EndpointId::from_str(phone_id)?;
        let mut addr = EndpointAddr::new(id);
        if let Some(url) = &self.relay_url {
            addr = addr.with_relay_url(RelayUrl::from_str(url)?);
        }
        let conn = timeout(STALL, self.endpoint.connect(addr, ALPN))
            .await
            .map_err(|_| JsError::new("Timed out reaching phone"))??;

        let bytes = match exchange(&conn, req, body).await {
            Ok(bytes) => bytes,
            Err(e) => {
                conn.close(1u32.into(), b"aborted");
                return Err(e);
            }
        };
        conn.close(0u32.into(), b"ok");

        let response = String::from_utf8_lossy(&bytes).into_owned();
        log(&format!("[bru] response from {phone_id}: {response}"));
        Ok(response)
    }
}
