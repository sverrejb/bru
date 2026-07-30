use std::cell::RefCell;
use std::str::FromStr;
use iroh::{Endpoint, EndpointId, SecretKey, endpoint::presets};
use wasm_bindgen::prelude::*;
use qrcode::{QrCode, render::svg};

const ALPN: &[u8] = b"bru/1";
const MAX_RESPONSE: usize = 16 * 1024 * 1024;

#[wasm_bindgen]
extern "C" {
    #[wasm_bindgen(js_namespace = console)]
    fn log(s: &str);
}

thread_local! {
    static ENDPOINT: RefCell<Option<Endpoint>> = const { RefCell::new(None) };
}

fn endpoint() -> Result<Endpoint, JsError> {
    ENDPOINT
        .with_borrow(|slot| slot.clone())
        .ok_or_else(|| JsError::new("bru_init not called"))
}

#[wasm_bindgen]
pub async fn bru_init(secret_key: &[u8]) -> Result<String, JsError> {
    let key: [u8; 32] = secret_key
        .try_into()
        .map_err(|_| JsError::new("secret key must be 32 bytes"))?;
    let endpoint = Endpoint::builder(presets::N0)
        .secret_key(SecretKey::from_bytes(&key))
        .alpns(vec![ALPN.to_vec()])
        .bind()
        .await
        .map_err(|e| JsError::new(&e.to_string()))?;
    let id = endpoint.id().to_string();
    ENDPOINT.with_borrow_mut(|slot| *slot = Some(endpoint));
    Ok(id)
}

#[wasm_bindgen]
pub async fn bru_online() -> Result<(), JsError> {
    let endpoint = endpoint()?;
    endpoint.online().await;
    Ok(())
}

#[wasm_bindgen]
pub async fn accept_pairing() -> Result<String, JsError> {
    let endpoint = endpoint()?;

    let incoming = endpoint
        .accept()
        .await
        .ok_or_else(|| JsError::new("endpoint closed"))?;
    let conn = incoming.await.map_err(|e| JsError::new(&e.to_string()))?;
    let phone_id = conn.remote_id().to_string();

    let (mut send, mut recv) = conn.accept_bi().await.map_err(|e| JsError::new(&e.to_string()))?;
    let bytes = recv.read_to_end(4096).await.map_err(|e| JsError::new(&e.to_string()))?;
    let message = String::from_utf8_lossy(&bytes);
    log(&message);
    send.write_all(br#"{"ok":true}"#).await.map_err(|e| JsError::new(&e.to_string()))?;
    send.finish().map_err(|e| JsError::new(&e.to_string()))?;
    conn.closed().await;

    Ok(format!(r#"{{"id":"{phone_id}","message":{message}}}"#))
}

async fn request(phone_id: &str, req: &[u8]) -> Result<String, JsError> {
    let endpoint = endpoint()?;
    let id = EndpointId::from_str(phone_id).map_err(|e| JsError::new(&e.to_string()))?;

    let conn = endpoint.connect(id, ALPN).await.map_err(|e| JsError::new(&e.to_string()))?;
    let (mut send, mut recv) = conn.open_bi().await.map_err(|e| JsError::new(&e.to_string()))?;
    send.write_all(req).await.map_err(|e| JsError::new(&e.to_string()))?;
    send.finish().map_err(|e| JsError::new(&e.to_string()))?;
    let bytes = recv.read_to_end(MAX_RESPONSE).await.map_err(|e| JsError::new(&e.to_string()))?;
    conn.close(0u32.into(), b"ok");

    Ok(String::from_utf8_lossy(&bytes).into_owned())
}

#[wasm_bindgen]
pub async fn bru_health(phone_id: &str) -> Result<String, JsError> {
    request(phone_id, br#"{"op":"health"}"#)
        .await
        .inspect(|s| log(s))
        .inspect_err(|e| log(&format!("{e:?}")))
}

#[wasm_bindgen]
pub fn generate_pairing_code(name: &str) -> Result<String, JsError> {
    let id = endpoint()?.id().to_string();
    let pairing_string = format!("https://bru.works/pair#d={}&n={}", id, name);
    let code = QrCode::new(&pairing_string).map_err(|e| JsError::new(&format!("QR encode failed: {e}")))?;

    Ok(code
        .render::<svg::Color>()
        .min_dimensions(512, 512)
        .light_color(svg::Color("#0000"))
        .build())
}

#[wasm_bindgen]
pub async fn get_messages(phone_id: &str, since: u32, limit: u32) -> Result<String, JsError> {
    request(phone_id, format!(r#"{{"op":"messages","since":{since},"limit":{limit}}}"#).as_bytes()).await
}