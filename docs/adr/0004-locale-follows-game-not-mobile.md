# Display-name locale follows the game, not the mobile app

The companion mod renders every `Item` display name in whatever Minecraft language is currently active on the game client, and reports that locale in the `Handshake.locale` field. The mobile app displays those names verbatim and uses the reported locale only for its own formatting concerns (date/number formatting, RTL layout, debug info).

Having the mobile app send its own locale and asking the mod to swap Minecraft's active language resource was rejected: it would require re-loading the language resource pack on the game thread per-connection, invasive and fragile, and would desync what the user sees in-game from what they see in the app. The current rule — "the app mirrors the game" — matches the player's expectation that the mobile view is a window into their running world.

The trade-off: users whose mobile OS language differs from their Minecraft language will see item names in the game's language. This is accepted because the alternative silently rewrites the user's chosen Minecraft configuration.
