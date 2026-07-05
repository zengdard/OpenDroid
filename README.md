# RAIS Orchestrator — AI On-Device Android Assistant

**Orchestrateur IA on-device pour Android, propulsé par Gemma 4 E2B via LiteRT-LM.**

## ✨ Fonctionnalités

- 🤖 **Gemma 4 E2B on-device** — Modèle Google DeepMind exécuté localement, 100% privé, sans Internet
- ⚡ **LiteRT-LM** — Inférence optimisée CPU/GPU avec caching et streaming token par token
- 🧠 **Chain of Thought** — Raisonnement visible en temps réel dans l'interface
- 🔧 **18 outils système** — SMS, appels, ouverture d'apps, UI, alarmes, partage, batterie, réseau…
- 🔍 **Découverte dynamique** — `get_installed_apps()` pour trouver les apps installées
- 📡 **Streaming temps réel** — Affichage des tokens au fur et à mesure de la génération
- 💬 **Multi-conversations** — Drawer latéral avec historique persistant
- 🎯 **Tool Calling** — Planification et exécution autonome d'actions

## 📦 Outils disponibles

| Outil | Description |
|---|---|
| `get_installed_apps` | Découvrir les applications installées |
| `send_sms` | Envoyer un SMS |
| `make_call` | Passer un appel |
| `open_app` | Ouvrir une application |
| `tap` / `type_text` | Interaction UI |
| `scroll` / `go_back` | Navigation écran |
| `get_battery_info` / `get_network_status` | Contexte appareil |
| `share_text` | Partager du contenu |
| `search_web` | Recherche web |
| `set_alarm` / `set_note` | Productivité |
| `ask_user_confirmation` | Sécurité |

## 🏗️ Architecture

```
app/
├── orchestrator/          # Moteur de planification et tool calling
│   ├── OrchestratorEngine.kt
│   ├── SystemPromptBuilder.kt
│   ├── ConversationHistory.kt
│   └── JsonParser.kt
├── tools/                 # 18 outils exécutables
├── ui/chat/               # Chat + streaming UI
│   ├── ChatScreen.kt      # Compose UI drawer + streaming
│   ├── ChatViewModel.kt   # Gestion état + inférence
│   └── LiteRTInference.kt # Interface LiteRT-LM
├── data/                  # DataStore, Repository, Manager
└── di/                    # Hilt injection
```

## 🚀 Build & Install

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 📋 Prérequis

- Android SDK 26+
- Appareil avec ~3 Go de RAM disponible
- Modèle Gemma 4 E2B (2.6 Go) téléchargé via l'app (Paramètres → Télécharger)

## 🛡️ Licence

Apache 2.0 — Projet RAIS Security
