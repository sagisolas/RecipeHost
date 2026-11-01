# Client-side companion mod with QR-paired TCP transport

The companion mod runs **client-side only** and exposes recipes/icons to the mobile app over a raw TCP socket carrying framed protobuf `Envelope`s. Pairing is per-session: opening the in-game QR screen generates a fresh 256-bit token, binds the listener, and shows the token + all non-loopback host addresses as a QR code. Exactly one `Session` is active at a time; the listener unbinds on world disconnect, on QR screen close + 30 s grace, or when the `Session` ends.

A server-side mod was rejected because correct icon rendering requires the Minecraft render thread (`GuiGraphics.renderItem`), which server-side has no access to. Reading PNGs out of the texture atlas would misrender ~5–10% of vanilla items (chests, shields, bundles, tridents) and most modded items with custom renderers.

HTTP was rejected in favour of raw-TCP-with-protobuf after we chose streamed frames over a one-shot response: the stream model benefits from a persistent connection and a tight message envelope, and the mobile side gains nothing from browser tooling in this context. Persistent pairing was rejected to keep the threat model minimal — no credentials stored on either device, listener bound only while the user is actively looking at the QR.
