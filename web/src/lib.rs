use iroh::{
    Endpoint, EndpointId, RelayMap, SecretKey, Watcher,
    endpoint::{RelayMode, presets},
};
use qrcode::{QrCode, render::svg};
use std::str::FromStr;
use wasm_bindgen::prelude::*;

const ALPN: &[u8] = b"bru/1";
const MAX_RESPONSE: usize = 16 * 1024 * 1024;
const PAIRING_URL: &str = "https://bru.works/pair";

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = console)]
    fn log(s: &str);
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
        let mut builder = Endpoint::builder(presets::N0)
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

    pub async fn accept_incoming(&self) -> Result<String, JsError> {
        log("[bru] accept_incoming: waiting for a connection");
        let conn = self
            .endpoint
            .accept()
            .await
            .ok_or_else(|| JsError::new("endpoint closed"))?
            .await?;
        let phone_id = conn.remote_id().to_string();
        log(&format!("[bru] accept_incoming: connection from {phone_id}"));

        let (mut send, mut recv) = conn.accept_bi().await?;
        let bytes = recv.read_to_end(4096).await?;
        let message = String::from_utf8_lossy(&bytes);
        log(&format!("[bru] accept_incoming: message={message}"));
        send.write_all(br#"{"ok":true}"#).await?;
        send.finish()?;
        conn.closed().await;

        Ok(format!(r#"{{"id":"{phone_id}","message":{message}}}"#))
    }

    pub async fn health(&self, phone_id: &str) -> Result<String, JsError> {
        self.request(phone_id, br#"{"op":"health"}"#).await
    }

    pub async fn messages(&self, phone_id: &str, since: u32, limit: u32) -> Result<String, JsError> {
        let req = format!(r#"{{"op":"messages","since":{since},"limit":{limit}}}"#);
        self.request(phone_id, req.as_bytes()).await
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
        self.request(phone_id, req.as_bytes()).await
    }

    pub async fn send_clipboard(&self, phone_id: &str, text: &str) -> Result<String, JsError> {
        let req = format!(r#"{{"op":"clipboard","text":{}}}"#, json_string(text));
        self.request(phone_id, req.as_bytes()).await
    }
}

impl Bru {
    async fn request(&self, phone_id: &str, req: &[u8]) -> Result<String, JsError> {
        log(&format!(
            "[bru] request to {phone_id}: {}",
            String::from_utf8_lossy(req)
        ));
        let id = EndpointId::from_str(phone_id)?;
        let conn = self.endpoint.connect(id, ALPN).await?;

        let (mut send, mut recv) = conn.open_bi().await?;
        send.write_all(req).await?;
        send.finish()?;
        let bytes = recv.read_to_end(MAX_RESPONSE).await?;
        conn.close(0u32.into(), b"ok");

        let response = String::from_utf8_lossy(&bytes).into_owned();
        log(&format!("[bru] response from {phone_id}: {response}"));
        Ok(response)
    }
}
