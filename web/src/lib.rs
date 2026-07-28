use std::cell::RefCell;
use iroh::{Endpoint, SecretKey, endpoint::presets};
use wasm_bindgen::prelude::*;
use qrcode::{QrCode, render::svg};

const ALPN: &[u8] = b"bru/1";

thread_local! {
    static ENDPOINT: RefCell<Option<Endpoint>> = const { RefCell::new(None) };
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
    let endpoint = ENDPOINT
        .with_borrow(|slot| slot.clone())
        .ok_or_else(|| JsError::new("bru_init not called"))?;
    endpoint.online().await;
    Ok(())
}

#[wasm_bindgen]
pub fn generate_pairing_code(text: &str) -> Result<String, JsError> {
    let code = QrCode::new(text).map_err(|e| JsError::new(&format!("QR encode failed: {e}")))?;
    Ok(code
        .render::<svg::Color>()
        .min_dimensions(512, 512)
        .build())
}
