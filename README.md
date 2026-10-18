# OpenDroid

**Agent IA autonome pour Android, inspiré d'[opencode](https://opencode.ai).**

OpenDroid est un assistant agentique natif qui pilote réellement votre téléphone :
il ouvre des apps, agit sur l'interface (taps, saisie, navigation), lit et écrit des
fichiers, exécute des commandes shell, envoie des SMS, passe des appels, consulte
l'appareil, recherche sur le web, programme des alarmes et délègue des sous-tâches
à d'autres agents.

Il fonctionne **on-device** avec Gemma (LiteRT-LM) ou avec n'importe quel
**provider distant** compatible OpenAI, Anthropic ou Google Gemini — au choix de
l'utilisateur.

---

## ✨ Fonctionnalités

- 🧠 **Agents multiples**, façon opencode : primaires (`build`, `plan`) et
  sous-agents (`general`, `explore`, `scout`) invocables via l'outil `task`.
- 🔒 **Permissions granulaires par outil** — `allow` / `ask` / `deny`, avec
  motifs glob, dernier match gagnant, surcharge par agent.
- 💬 **Sessions & historique** persistés en JSON, titre auto-raffiné, reprise à volonté.
- ✅ **Suivi de tâches** (`todowrite` / `todoread`) affiché dans l'UI.
- 📡 **Streaming temps réel** du raisonnement (`thinking`) et de la réponse.
- 🔌 **Providers interchangeables** : on-device (Gemma), OpenAI, Anthropic,
  Google Gemini, OpenRouter, Groq, Ollama, LM Studio, ou tout endpoint
  OpenAI-compatible personnalisé.
- 🖥️ **Contrôle de l'appareil** via un service d'accessibilité intégré.
- 🛠️ **~40 outils** répartis en catégories (fichiers, shell, web, agents, apps,
  interface, appareil, communication).
- 🎨 **UI Compose Material 3** moderne (clair/sombre), drawer de sessions.

---

## 🧩 Agents

| Agent | Mode | Rôle |
|---|---|---|
| `build` | primaire | Tous les outils activés — tâches complètes de bout en bout |
| `plan`  | primaire | Lecture seule : analyse et propose un plan sans agir |
| `general` | sous-agent | Polyvalent, recherche et tâches multi-étapes |
| `explore` | sous-agent | Exploration rapide en lecture seule |
| `scout` | sous-agent | Recherche web / documentation |

Chaque agent possède un prompt, un modèle optionnel, une température, un nombre
max d'itérations (`steps`) et ses propres permissions.

---

## 🔒 Permissions

Les permissions sont évaluées comme dans opencode :

- Clé = nom de l'outil (`read`, `edit`, `bash`, `open_app`, `send_sms`, …) ou `*`.
- Chaque clé contient des règles `motif → niveau` (`allow`/`ask`/`deny`).
- Le motif est comparé à l'entrée de l'outil (commande, URL, chemin, package…).
- **La dernière règle correspondante l'emporte.**
- Les règles de l'agent s'appliquent avant les règles globales.
- Un outil en `deny` est **masqué du modèle**.
- Mode `ask` → une boîte de dialogue propose « Autoriser une fois » / « Refuser ».
- Un interrupteur **Auto-approuver** ignore les demandes `ask`.

Écran dédié : **Paramètres → Permissions** (sélecteur à 3 états par outil).

---

## 🛠️ Outils

**Fichiers / code** — `read`, `write`, `edit`, `glob`, `grep`, `list`
**Shell** — `bash`
**Web** — `webfetch`, `websearch`
**Agents** — `task`, `question`, `todowrite`, `todoread`
**Applications** — `list_apps`, `app_info`, `open_app`, `open_url`, `open_settings`
**Interface** — `screen_dump`, `tap`, `long_press`, `swipe`, `type_text`, `press_key`, `find_tap`
**Appareil** — `device_info`, `get_battery_info`, `get_network_status`, `get_location`,
`clipboard_get`, `clipboard_set`, `notification_send`, `notifications_list`,
`media_control`, `take_screenshot`
**Communication** — `send_sms`, `make_call`, `share_text`, `contacts_search`,
`set_alarm`, `create_event`, `list_events`

---

## 🔌 Providers

| Provider | Type | Notes |
|---|---|---|
| On-device (Gemma) | `ON_DEVICE` | LiteRT-LM, protocole d'outils XML, 100 % local |
| **OpenCode Zen** | `OPENAI_COMPATIBLE` | `opencode.ai/inference/openai/v1` — clé `OPENCODE_API_KEY` |
| **OpenCode Go** | `OPENAI_COMPATIBLE` | `opencode.ai/inference/go/openai/v1` — offre low-cost |
| OpenAI | `OPENAI_COMPATIBLE` | `api.openai.com/v1` |
| Anthropic | `ANTHROPIC` | API Messages, thinking étendu |
| Google Gemini | `GOOGLE` | `streamGenerateContent` |
| OpenRouter / Groq / Ollama / LM Studio | `OPENAI_COMPATIBLE` | presets inclus |
| Personnalisé | `OPENAI_COMPATIBLE` | base URL + clé + modèle |

Les clés API sont saisies dans **Paramètres → Providers** et stockées localement.
Le modèle on-device se télécharge depuis **Paramètres → Modèle on-device**.

---

## 🏗️ Architecture

```
app/src/main/java/com/opendroid/
├── OpenDroidApp.kt            # Application Hilt (canal notif, préchargement modèle)
├── MainActivity.kt            # Navigation Compose
├── accessibility/             # Service d'accessibilité + pont (gestes, arbre de vues, capture)
├── agent/                     # Boucle d'agent (LLM → outils → LLM), prompts, événements
├── data/
│   ├── model/                 # Message, Session, ToolSpec, Permission, AgentConfig, ProviderConfig
│   ├── registry/              # Agents intégrés (BuiltInAgents)
│   ├── session/               # SessionStore (JSON sur disque)
│   └── settings/              # SettingsStore (DataStore)
├── di/                        # Module Hilt
├── provider/                  # Contrat LlmProvider + implémentations + XmlToolParser
│   └── local/                 # LocalInference (LiteRT-LM)
├── tool/                      # Contrat Tool, ToolContext, ToolRegistry
│   └── impl/                  # Tous les outils
└── ui/                        # Chat, réglages, composants, thème
```

La boucle d'agent (`AgentRunner`) : pour chaque itération, elle envoie l'historique
au provider, streame la réponse, exécute les appels d'outils demandés (après
vérification des permissions) et reboucle jusqu'à une réponse finale ou la limite
d'itérations. Les sous-agents réutilisent la même boucle avec leur propre config.

---

## 🚀 Build & install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📋 Prérequis

- Android 8.0+ (API 26)
- Java 17 pour la compilation
- Pour l'usage on-device : ~3 Go de RAM libre et le modèle Gemma LiteRT-LM
  téléchargé depuis l'app
- Activer le service d'accessibilité OpenDroid (Paramètres → Accessibilité)
  pour les outils d'interface

## 🛡️ Licence

Apache 2.0
