# Bru

A **B**idirectional **R**emote **U**plink (also Norwegian for "bridge").

Sync your phone and computer over an end-to-end-encrypted
[iroh](https://iroh.computer) link. No cloud account, no external databses, no port
forwarding or fiddling with network settings.

* **Read and send your phone's SMS**. Conveniently from a desktop, no need to pull out your phone. 

* **Send and recieve text to clipboard.** No more typing things manually or struggeling with long URL's you rather browse on your computer.


> Built on iroh, which kind of makes this *iroh-bru*. No relation to Scotland's
> **other** national drink.

## Installing the Android app

Not on F-Droid or Google Play yet. Until then, you can build the APK yourself, using Docker.

```sh
git clone https://github.com/sverrejb/bru
cd bru
docker build -t bru-apk android
mkdir -p out && docker run --rm -v "$PWD/out:/out" bru-apk
```

The image is pinned to `linux/amd64` because Google only ships x86_64 build tools, so on an ARM machine the build runs emulated and takes around ten minutes. Elsewhere it is a couple of minutes.

You now have two APKs in `out/`:


* `app-arm64-debug.apk`: 64-bit ARM, which is every reasonably modern phone
* `app-armv7-debug.apk`: Older 32-bit devices  (not tested, who knows if it will work at all 🤷‍♂️)

Install it over USB with `adb install out/app-arm64-debug.apk`, or copy the
file to the phone and open it. Android will ask you to allow installing from that app the first time.

These are debug-signed builds, so Android shows the usual "unknown developer" warning. Play Protect may also warn about an unrecognised app.

## License

Licensed under either of [Apache License 2.0](LICENSE-APACHE) or
[MIT license](LICENSE-MIT) at your option.
