use iroh::{Endpoint, EndpointId, SecretKey, endpoint::presets};
use qrcode::{QrCode, render::svg};
use std::str::FromStr;
use wasm_bindgen::prelude::*;

const ALPN: &[u8] = b"bru/1";
const MAX_RESPONSE: usize = 16 * 1024 * 1024;
const PAIRING_URL: &str = "https://bru.works/pair";

#[wasm_bindgen]
extern "C" {
    #[allow(dead_code)]
    #[wasm_bindgen(js_namespace = console)]
    fn log(s: &str);
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
        Ok(Bru { endpoint })
    }

    pub fn id(&self) -> String {
        self.endpoint.id().to_string()
    }

    pub async fn online(&self) {
        self.endpoint.online().await;
    }

    pub fn pairing_code(&self, name: &str) -> Result<String, JsError> {
        let code = QrCode::new(format!("{PAIRING_URL}#d={}&n={name}", self.id()))?;
        Ok(code
            .render::<svg::Color>()
            .min_dimensions(512, 512)
            .light_color(svg::Color("#0000"))
            .build())
    }

    pub async fn accept_pairing(&self) -> Result<String, JsError> {
        let conn = self
            .endpoint
            .accept()
            .await
            .ok_or_else(|| JsError::new("endpoint closed"))?
            .await?;
        let phone_id = conn.remote_id().to_string();

        let (mut send, mut recv) = conn.accept_bi().await?;
        let bytes = recv.read_to_end(4096).await?;
        let message = String::from_utf8_lossy(&bytes);
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
        let req =
            format!(r#"{{"op":"send","to":{to:?},"body":{body:?},"clientId":{client_id:?}}}"#);
        self.request(phone_id, req.as_bytes()).await
    }
}

impl Bru {
    async fn request(&self, phone_id: &str, req: &[u8]) -> Result<String, JsError> {
        let id = EndpointId::from_str(phone_id)?;
        let conn = self.endpoint.connect(id, ALPN).await?;

        let (mut send, mut recv) = conn.open_bi().await?;
        send.write_all(req).await?;
        send.finish()?;
        let bytes = recv.read_to_end(MAX_RESPONSE).await?;
        conn.close(0u32.into(), b"ok");

        Ok(String::from_utf8_lossy(&bytes).into_owned())
    }
}
