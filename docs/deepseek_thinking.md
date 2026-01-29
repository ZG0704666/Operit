# DeepSeek Thinking / Thinking Guidance

## Overview
Operit exposes two user-facing switches under **Thinking**:

- **Thinking Mode**: enables provider-level "thinking" (when the provider supports it).
- **Thinking Guidance**: prompt-level guidance to encourage models to think (works even when the provider has no explicit thinking switch).

These two options are **mutually exclusive** at the configuration layer to avoid double-activation.

## DeepSeek official API fields
According to DeepSeek official API docs for `POST /chat/completions`:

- Request body supports a `thinking` field.
- `thinking` is an object used to switch thinking on/off.
- Documented values are:
  - `{"type": "enabled"}`
  - `{"type": "disabled"}`

The official docs do **not** describe additional sub-fields such as `low/high`, `quality`, `effort`, or `budget_tokens` for the `thinking` object.

Related docs:
- https://api-docs.deepseek.com/api/create-chat-completion
- https://api-docs.deepseek.com/guides/thinking_mode

## How Operit sends DeepSeek requests today
- When **Thinking Mode** is enabled, `DeepseekProvider` injects:

```json
{
  "thinking": { "type": "enabled" }
}
```

- When Thinking Mode is disabled, the request does not include the `thinking` field.

Notes:
- DeepSeek docs describe `max_tokens` as the maximum output length **including** the chain-of-thought part.
- Operit does not currently add any undocumented "thinking quality" parameters to DeepSeek requests.

## Thinking Quality setting
Operit has an internal preference/plumbing for a "thinking quality" level, but **the SettingBar UI is currently hidden** and the provider request does not apply it.

This was intentionally kept so it can be re-enabled later if DeepSeek documents a stable quality parameter or if we decide to map it to a supported field (e.g. `max_tokens`) with clear UX.

## Mutual exclusivity behavior
At the preferences/config layer:
- Enabling **Thinking Mode** will disable **Thinking Guidance**.
- Enabling **Thinking Guidance** will disable **Thinking Mode**.

Additionally, the message sending pipeline avoids enabling provider-level thinking when guidance is enabled (safety guard).
