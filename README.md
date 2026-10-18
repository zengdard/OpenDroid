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

- 🧠 **Agents multiples**, façon opencode : primaires (`chat`, `build`, `plan`) et
  sous-agents (`general`, `explore`, `scout`, `checker`) invocables via l'outil `task`.
- 🔒 **Permissions granulaires par outil** — `allow` / `ask` / `deny`, avec
  motifs glob, dernier match gagnant, surcharge par agent.
- 💬 **Sessions & historique** persistés en JSON, titre auto-raffiné, reprise à volonté.
- ✅ **Suivi de tâches** (`todowrite` / `todoread`) affiché dans l'UI.
- 📡 **Streaming temps réel** du raisonnement (`thinking`) et de la réponse.
- 🔌 **Providers interchangeables** : on-device (Gemma), OpenCode Zen/Go, DeepSeek,
  OpenAI, Anthropic, Google Gemini, OpenRouter, Groq, Ollama, LM Studio, ou tout
  endpoint OpenAI-compatible personnalisé.
- 🖥️ **Contrôle de l'appareil** via un service d'accessibilité intégré.
- ⌨️ **Terminal intégré** — shell de l'app, **environnement Termux** (`RUN_COMMAND`)
  ou root (`su`), avec historique et commandes rapides. Les agents disposent des
  outils `bash` et `termux`.
- 🛠️ **~40 outils** répartis en catégories (fichiers, shell, web, agents, apps,
  interface, appareil, communication) + **outils MCP** dynamiques.
- 🎨 **UI épurée façon iOS** (palette neutre, accent bleu, cartes groupées, bulles
  iMessage) en clair et sombre.

---

## 🧩 Agents

| Agent | Mode | Rôle |
|---|---|---|
| `chat` | primaire | **Chatbot pur** : conversation, aucun outil, aucune action |
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
**Shell** — `bash` (shell de l'app, `root=true` pour `su`), `termux` (environnement Termux)
**Web** — `webfetch`, `websearch`
**Agents** — `task`, `question`, `todowrite`, `todoread`
**Applications** — `list_apps`, `app_info`, `open_app`, `open_url`, `open_settings`
**Interface** — `screen_dump`, `tap`, `tap_id`, `long_press`, `swipe`, `type_text`, `press_key`, `find_tap`, `verify`
**Appareil** — `device_info`, `get_battery_info`, `get_network_status`, `get_location`,
`clipboard_get`, `clipboard_set`, `notification_send`, `notifications_list`,
`media_control`, `take_screenshot`
**Communication** — `send_sms`, `make_call`, `share_text`, `contacts_search`,
`set_alarm`, `create_event`, `list_events`

---

## 🚀 Capacités avancées (inspirées d'ARTEMIS)

- **Ciblage par index + garde-fou pré-action** — `screen_dump` renvoie des éléments
  indexés (`#12 TextView "Battery" @(540,1200) [clickable]`) ; `tap_id` vérifie que
  l'élément est toujours présent (même paquet, mêmes bounds, même libellé) avant
  d'agir, au lieu de taper sur des coordonnées périmées.
- **Planner / Operator / Checker** — `todowrite` accepte un champ `verify` par étape ;
  l'outil `verify` contrôle un checkpoint sur l'écran vivant (texte présent/absent,
  paquet actif) ; le sous-agent `checker` audite le résultat final (lecture seule).
- **Compression d'historique** — au-delà d'un budget de contexte, les anciens tours
  sont résumés en un mémo compact et seuls les 8 derniers messages sont conservés :
  sessions longues possibles même avec un petit contexte on-device.
- **Vision** — `take_screenshot` joint la capture au modèle (OpenAI/Anthropic/Gemini)
  pour analyser les interfaces Canvas/Compose/Flutter sans arbre d'accessibilité.
- **MCP (Model Context Protocol)** — connecte des serveurs d'outils externes en
  HTTP JSON-RPC (`initialize` → `tools/list` → `tools/call`), avec support SSE et
  `Mcp-Session-Id`. Les outils découverts sont exposés sous la clé de permission
  `mcp` et nommés `mcp_<serveur>_<outil>`. Configuration : **Paramètres → Serveurs MCP**.
- **Terminal** — écran dédié (icône dans la barre du chat, ou Paramètres → Terminal) :
  exécution de commandes shell, sortie colorée (stdout/stderr/exit), commandes rapides,
  et sélecteur de backend **App / Termux / Root** (root affiché uniquement si `su` existe).

### Termux

Les agents peuvent exécuter des commandes dans le **vrai environnement Termux**
(bash, `pkg`/`apt`, Python, git…) via l'intent officiel `RUN_COMMAND`.

Mise en place (une fois) :

1. Installer **Termux** depuis [F-Droid](https://f-droid.org/packages/com.termux/)
   (la version Play Store est obsolète).
2. Dans Termux, autoriser les apps externes :
   ```bash
   mkdir -p ~/.termux
   echo "allow-external-apps=true" >> ~/.termux/termux.properties
   termux-reload-settings
   ```
3. Accorder la permission à OpenDroid : **Paramètres Android → Apps → OpenDroid →
   Permissions → Autorisations supplémentaires → « Exécuter des commandes dans
   l'environnement Termux »**. En ADB :
   ```bash
   adb shell pm grant com.opendroid com.termux.permission.RUN_COMMAND
   ```
4. Dans OpenDroid : **Terminal → puce « Termux »**, ou demander à l'agent
   (« liste mes paquets Termux », « lance un script python… ») — l'outil `termux`
   est disponible pour les agents.

Les commandes tournent en arrière-plan et renvoient stdout, stderr et le code de
sortie. Sans Termux installé, l'outil et l'onglet restent indisponibles avec un
message explicite.

L'outil `bash` **route automatiquement vers Termux** les commandes qui en relèvent
(`pkg`, `apt`, `pip`, `python`, `git`, `node`, `termux`, `$PREFIX`), même si le
modèle appelle `bash` par habitude. Pour les autres commandes, `bash` reste le
shell minimal de l'app.

---

## 🔌 Providers

| Provider | Type | Notes |
|---|---|---|
| On-device (Gemma) | `ON_DEVICE` | LiteRT-LM, protocole d'outils XML, 100 % local |
| **OpenCode Zen** | `OPENAI_COMPATIBLE` | `opencode.ai/inference/openai/v1` — clé `OPENCODE_API_KEY` |
| **OpenCode Go** | `OPENAI_COMPATIBLE` | `opencode.ai/inference/go/openai/v1` — offre low-cost |
| **DeepSeek** | `OPENAI_COMPATIBLE` | `api.deepseek.com/v1` — `deepseek-flash` (**texte + image**), `deepseek-v4-pro` (texte) |
| OpenAI | `OPENAI_COMPATIBLE` | `api.openai.com/v1` |
| Anthropic | `ANTHROPIC` | API Messages, thinking étendu |
| Google Gemini | `GOOGLE` | `streamGenerateContent` |
| OpenRouter / Groq / Ollama / LM Studio | `OPENAI_COMPATIBLE` | presets inclus |
| Personnalisé | `OPENAI_COMPATIBLE` | base URL + clé + modèle |

> **OpenCode Zen / Go** : renseigne la clé `OPENCODE_API_KEY` (Console → Create API key).
> Les endpoints Go exigent un **User-Agent dédié** (`opendroid/1.0`) et un identifiant de
> session stable dans le header **`x-opencode-session`** — OpenDroid l'envoie
> automatiquement (ID = session de conversation).
> Tous les modèles Go ne passent pas par `/chat/completions` : les modèles `qwen*`,
> `minimax-m3` et `claude-haiku-5-5` utilisent l'API *anthropic*, `grok*`, `gpt-*-luna`
> et `muse-spark*` l'API *responses*, et Gemini l'API *google*. Les modèles listés dans
> le preset Go sont ceux servis via `/chat/completions` (deepseek, kimi, glm, mimo,
> longcat, hy, step, beam…).
> L'écran provider propose un bouton **Tester** pour valider la clé et l'endpoint.

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
