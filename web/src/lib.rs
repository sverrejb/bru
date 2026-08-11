use iroh::{Endpoint, EndpointId, SecretKey, endpoint::presets};
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

// Rust's `{:?}` escapes non-printable Unicode (e.g. emoji variation selectors, ZWJ) as
// `\u{XXXX}`, which isn't valid JSON (JSON needs exactly 4 hex digits, no braces) and breaks
// the phone's JSON parser on message bodies containing them. This escapes only what JSON
// requires and leaves everything else as raw UTF-8, which JSON strings allow unescaped.
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
}

#[wasm_bindgen]
impl Bru {
    pub async fn open(secret_key: &[u8]) -> Result<Bru, JsError> {
        let key: [u8; 32] = secret_key.try_into()?;
        let endpoint = Endpoint::builder(presets::N0)
            .secret_key(SecretKey::from_bytes(&key))
            .alpns(vec![ALPN.to_vec()])
            .bind()
            .await?;
        log(&format!("[bru] endpoint bound, id={}", endpoint.id()));
        Ok(Bru { endpoint })
    }

    pub fn id(&self) -> String {
        self.endpoint.id().to_string()
    }

    pub async fn online(&self) {
        self.endpoint.online().await;
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
