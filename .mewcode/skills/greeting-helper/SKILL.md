# greeting-helper

Generates personalized greetings based on a given name and optional context (time of day, language, tone).

## Inputs

| Parameter    | Required | Description                                      |
|-------------|----------|--------------------------------------------------|
| `name`      | Yes      | The name of the person to greet.                 |
| `timeOfDay` | No       | One of: `morning`, `afternoon`, `evening`. Defaults to a generic greeting. |
| `language`  | No       | Language code (e.g., `en`, `es`, `fr`). Defaults to `en`. |
| `tone`      | No       | One of: `casual`, `formal`, `enthusiastic`. Defaults to `casual`. |

## Instructions

1. Read the `name` parameter. If not provided, ask the user for it.
2. Use `timeOfDay` to select an appropriate opening (e.g., "Good morning", "Good evening"). If omitted, use a generic opener like "Hello".
3. Apply `tone` modifiers:
   - **casual** – "Hey {name}!", "Hi {name}!"
   - **formal** – "Good {timeOfDay}, {name}. I hope this message finds you well."
   - **enthusiastic** – "Hey {name}!! So great to see you!"
4. If `language` is not `en`, translate the greeting while preserving the tone.
5. Return only the greeting string—no extra commentary.

## Examples

**Input:** `name="Alice"`, `timeOfDay="morning"`, `tone="casual"`
**Output:** `Good morning, Alice!`

**Input:** `name="Bob"`, `tone="enthusiastic"`
**Output:** `Hey Bob!! So great to see you!`

**Input:** `name="Carlos"`, `timeOfDay="evening"`, `tone="formal"`, `language="es"`
**Output:** `Buenas noches, Carlos. Espero que este mensaje le encuentre bien.`
