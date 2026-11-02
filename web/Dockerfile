FROM rust:trixie AS build
COPY . /app
WORKDIR /app
RUN apt-get update -qq && apt-get install -qq -y --no-install-recommends npm clang && npm install -g wasm-pack typescript && rm .cargo/config.toml
RUN wasm-pack build --target web --no-pack --release --out-dir dist/pkg
RUN tsc --noEmit -p dist/jsconfig.json

FROM nginx
COPY --from=build /app/dist /usr/share/nginx/html
