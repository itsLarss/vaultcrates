<div align="center">

<img src="logo.png" alt="VaultCrates Logo" width="128"/>

# VaultCrates

**A feature-rich, fully animated crate plugin for Paper 1.21.x**

No NMS · No legacy libraries · No external hologram plugin required

[![Paper](https://img.shields.io/badge/Paper-1.21.x-F96854?style=flat-square&logo=data:image/png;base64,iVBORw0KGgo=)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-21+-007396?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Modrinth](https://img.shields.io/modrinth/dt/vaultcrates?style=flat-square&logo=modrinth&color=00AF5C)](https://modrinth.com/plugin/vaultcrates)
[![License](https://img.shields.io/badge/License-All%20Rights%20Reserved-red?style=flat-square)](#-license)

</div>

---

## ✨ Features

- **12 unique animations** — particles, sounds and visual effects for every style
- **Physical & Virtual Keys** — NBT-tagged items with anti-dupe UUID tracking; virtual keys stored per-player in the database
- **Pity system** — configurable guaranteed drop after N opens per rarity
- **Reward limits** — global and per-player caps per reward
- **Virtual key shop** — players buy keys with in-game money via `/vc shop` (requires Vault)
- **Pouches** — right-clickable reward bags with configurable random number ranges
- **Holograms** — floating Text Display entities, no external plugin needed; persist across restarts
- **Placement ID system** — every placed crate gets a unique ID; remove via `/vc remove <id>`
- **In-game GUI editor** — edit crates and keys without touching a YAML file
- **Reward preview** — paginated GUI with normalised drop percentages; toggle-able per crate
- **Best Prizes** — guaranteed bonus reward rolled from a separate pool
- **Bundled items** — give multiple bonus items alongside the main reward
- **Selectable rewards** — players pick their prize from N random options
- **Milestones** — bonus rewards after X total crate opens
- **Reward footer** — auto-appended lore lines showing reward type and rarity (configurable template)
- **NPC support** — Citizens, ZNPCs, ZNPCsPlus, FancyNpcs, or any entity via `/vc linknpc`
- **Multi-language** — English, German, French, Spanish built-in; add any language by dropping a `messages_<code>.yml` file
- **Multi-database** — JSON (default), SQLite or MySQL with automatic table creation
- **Developer API** — custom events, programmatic crate opening, key management

---

## 📋 Requirements

| | |
|---|---|
| Server | **Paper** (or forks) 1.21 – 1.21.4+ |
| Java | **21+** |

### Optional soft-dependencies

| Plugin | Purpose |
|---|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) + economy plugin | Key shop, economy prices in reward commands |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Placeholders in messages & hologram lines |
| [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/) | Custom block & item models |
| [Oraxen](https://www.spigotmc.org/resources/oraxen.72448/) | Custom block & item models |
| [Nexo](https://www.spigotmc.org/resources/nexo.112733/) | Custom block & item models |
| [ExecutableItems](https://www.spigotmc.org/resources/custom-items-textures-executable-items.77578/) | Custom items as rewards |
| [MMOItems](https://www.spigotmc.org/resources/mmoitems-premium.39267/) | Custom items as rewards |
| [Citizens](https://www.spigotmc.org/resources/citizens.13811/) | NPC-to-crate linking |
| [ZNPCs](https://www.spigotmc.org/resources/znpcs.80940/) / [ZNPCsPlus](https://hangar.papermc.io/Pyrbu/ZNPCsPlus) | NPC-to-crate linking |
| [FancyNpcs](https://www.spigotmc.org/resources/fancynpcs.111694/) | NPC-to-crate linking |

> All soft-dependencies are loaded via **reflection** — VaultCrates compiles and runs without any of them installed.

---

## 🚀 Installation

1. Drop `VaultCrates.jar` into your `plugins/` folder
2. Start or restart the server — example configs are generated automatically
3. Get a placeable crate block: `/vc getcrate ExampleRound`
4. Place it anywhere — the hologram appears and the placement is saved automatically
5. Give yourself a key: `/vc key give <name> ExampleRound 1`
6. Right-click the crate to open it

---

## 💬 Commands

All commands use `/vc` (aliases: `/vaultcrates`, `/crate`).

### Crate management

| Command | Permission | Description |
|---|---|---|
| `/vc getcrate <crate>` | `vaultcrates.admin` | Get the placeable crate block item |
| `/vc create <name>` | `vaultcrates.admin` | Create a new crate config in-game |
| `/vc list` | `vaultcrates.admin` | List all loaded crates |
| `/vc errors` | `vaultcrates.admin` | Show config load errors |
| `/vc reload` | `vaultcrates.admin` | Reload all configs and holograms |
| `/vc editor <crate>` | `vaultcrates.admin` | Open the in-game GUI editor |
| `/vc keyeditor <crate>` | `vaultcrates.admin` | Open the in-game key editor |

### Placement management

| Command | Permission | Description |
|---|---|---|
| `/vc placements` | `vaultcrates.admin` | List all placed instances with IDs and coordinates |
| `/vc remove <id>` | `vaultcrates.admin` | Remove a placed crate instance by its ID |

> **Tip:** **Shift+Left-Click** a crate block to break and remove it (requires `vaultcrates.admin`).
> The unique placement ID is shown in chat when placing a crate — note it for later removal.

### Physical keys (items)

| Command | Permission | Description |
|---|---|---|
| `/vc give <player> <crate> [amount]` | `vaultcrates.give` | Give a physical key |
| `/vc sgive <player> <crate> [amount]` | `vaultcrates.give` | Give silently |
| `/vc giveall <crate> [amount]` | `vaultcrates.give` | Give to all online players |

### Virtual keys

| Command | Permission | Description |
|---|---|---|
| `/vc key give <player> <crate> [amount]` | `vaultcrates.key` | Add virtual keys |
| `/vc key sgive <player> <crate> [amount]` | `vaultcrates.key` | Add silently |
| `/vc key giveall <crate> [amount]` | `vaultcrates.key` | Give to all online |
| `/vc key set <player> <crate> <amount>` | `vaultcrates.key` | Set key balance |
| `/vc key balance <player> <crate>` | `vaultcrates.key` | Check key balance |
| `/vc key withdraw <player> <crate> [amount]` | `vaultcrates.key` | Remove virtual keys |
| `/vc shop` | `vaultcrates.shop` | Open the virtual key shop |

### Pouches

| Command | Permission | Description |
|---|---|---|
| `/vc pouch give <player> <pouch> [amount]` | `vaultcrates.pouch` | Give a pouch |
| `/vc pouch sgive <player> <pouch> [amount]` | `vaultcrates.pouch` | Give silently |
| `/vc pouch giveall <pouch> [amount]` | `vaultcrates.pouch` | Give to all online |
| `/vc pouch list` | `vaultcrates.pouch` | List all loaded pouches |

### NPC linking

| Command | Permission | Description |
|---|---|---|
| `/vc linknpc <crate>` | `vaultcrates.linknpc` | Link the nearest entity/NPC to a crate |

---

## 🔑 Permissions

| Permission | Default | Description |
|---|---|---|
| `vaultcrates.*` | op | All permissions |
| `vaultcrates.admin` | op | Place/break crates, reload, editor, getcrate, placements, remove |
| `vaultcrates.open` | true | Open crates |
| `vaultcrates.give` | op | Give physical keys |
| `vaultcrates.key` | op | Manage virtual keys |
| `vaultcrates.shop` | true | Open the virtual key shop |
| `vaultcrates.linknpc` | op | Link entities/NPCs to crates |
| `vaultcrates.pouch` | op | Give pouches |
| `vaultcrates.bypass` | op | Bypass cooldowns and blocked commands |

---

## ⚙️ Configuration

### `config.yml` — key settings

```yaml
# Language: en (default) | de | fr | es | or any custom locale code
Language: en

Storage:
  Type: JSON          # JSON (default) | SQLITE | MYSQL
  KeyUuidExpiryDays: 365

Settings:
  Cant_Open_Creative: false
  Must_Hold_Key_In_Hand: false
  Cant_Drop_Key: false
  Blocked_Commands: []  # Commands blocked while an animation is running

Keys:
  Virtual_Keys_Enabled: true
  Physical_Keys_Enabled: true

Shop:
  Enabled: true
  VirtualKeys: true   # true = virtual keys; false = physical key items

Win_Message: "&8[&6VaultCrates&8] &7You won {reward_name} [{rarity}] from &e{crate_name}&7!"

RewardFooter:         # Extra lore lines appended to every reward item
  Enabled: false
  Lines:
    - ""
    - "&8Reward Type » &7{reward_type}"
    - "&8Rarity » {rarity_color}{rarity_name}"
```

### Crate config (`crates/MyCrate.yml`)

```yaml
Animation: ROUND          # See animations list
Block: ENDER_CHEST
Size: 1                   # Rewards given per open
PreviewEnabled: true
ShowChanceInPreview: true
ShiftInstantlyOpen: false

HologramLines:
  - "&6&lMy Crate"
  - "&7Right-click to open"

KeyCrate:
  Require: true
  KeysRequired: 1
  Material: TRIPWIRE_HOOK
  Name: "&6&lMy Crate Key"
  MatchNBT: true

FinalMessage: []          # Broadcast to all players on open (leave empty to disable)

Prizes:
  diamond:
    Name: "&b&lDiamond"
    Rarity: epic           # common | rare | epic | legendary | jackpot | mega_jackpot
    Chance: 10.0           # Relative weight — does not need to sum to 100
    Material: DIAMOND
    Amount: 1
    Glow: true
    GiveItem: true
    Type: "Item"           # Optional — shown in RewardFooter as {reward_type}
    Commands: []
    EnchantCommands: []    # Run after item is given (for custom enchant plugins)
    MessagesToPlayer: []
    BroadcastMessages: []
    GlobalLimit: 0         # 0 = unlimited
    PlayerLimit: 0

BestPrizes:
  legendary_item:
    Name: "&6&lLegendary Sword"
    Chance: 100.0
    Material: NETHERITE_SWORD
    GiveItem: true
```

### Available rarities

| ID | Display | Glow colour |
|---|---|---|
| `common` | &7Common | Gray |
| `rare` | &aRare | Green |
| `epic` | &5Epic | Purple |
| `legendary` | &6&lLegendary | Gold |
| `jackpot` | &c&lJackpot | Red |
| `mega_jackpot` | &4&l✦ Mega Jackpot ✦ | Dark red |

Custom rarities can be added in `config.yml` under `Rarities:` with optional pity thresholds, glow colours and broadcast messages.

### Pouch config (`pouches/MyPouch.yml`)

```yaml
Name: "MoneyPouch"
DisplayName: "&6&lMoney Pouch"
Material: SUNFLOWER
Glow: true
Commands:
  - "eco give {player_name} {random_number}"
MinNumber: 500
MaxNumber: 5000
```

---

## 🌐 Multi-language

Four languages are bundled out of the box:

| Code | Language |
|---|---|
| `en` | English (default) |
| `de` | German / Deutsch |
| `fr` | French / Français |
| `es` | Spanish / Español |

**Using a custom or regional language:**

1. Create `messages_pt_br.yml` (or any `messages_<code>.yml`) in the plugin folder
2. Set `Language: pt_br` in `config.yml`
3. Run `/vc reload`

**Fallback chain:** `messages_en_us.yml` → `messages_en.yml` → `messages.yml`

---

## 🗄️ Storage backends

| Type | Notes |
|---|---|
| `JSON` | Default. Zero setup. All data in `data/`. |
| `SQLITE` | File-based SQL. Bundled driver — no extra JAR needed. |
| `MYSQL` | Full MySQL / MariaDB support. Tables created automatically. |

Data stored: virtual keys, pity counters, reward limits, milestone progress, used key UUIDs.

---

## 🎬 Animations

| Name | Style |
|---|---|
| `ROUND` | Slot machine — items cycle, slow down and lock on the winner |
| `ROUND2` | Mystery boxes — BARRIER boxes orbit; lightning reveals prizes |
| `QUICK` | Slot machine — same as ROUND but faster (~3 s) |
| `COSMIC` | Orbital — double counter-rotating rings with bobbing centre item |
| `DISPLAY` | Single item cycles through prizes and lands on the winner |
| `PYRAMID` | Three-tiered pyramid of rotating items |
| `CONTRABAND` | Items float upward and vanish one-by-one |
| `INSTANT` | No animation — particle burst, reward given immediately |
| `AIR_STRIKE` | Items fall from height with lightning strikes |
| `BREAKOUT` | Items burst outward and arc back |
| `METEOR_SHOWER` | Items rain down with flame trails |
| `YIN_YANG` | Two rings converge in a yin-yang pattern |

---

## 🎨 Custom item support

```yaml
# In a reward, crate block, or key:
OraxenModel:  "namespace:item_id"
NexoModel:    "namespace:item_id"
IAModel:      "namespace:item_id"   # ItemsAdder
EIModel:      "my_item_id"          # ExecutableItems
MMOItem:      "TYPE:item_id"        # e.g. SWORD:my_sword
```

---

## 📊 PlaceholderAPI

| Placeholder | Returns |
|---|---|
| `%vaultcrates_keys_<CrateName>%` | Virtual key count for that crate |
| `%vaultcrates_opens_<CrateName>%` | Total opens by the player |
| `%vaultcrates_pity_<CrateName>_<rarityId>%` | Current pity counter |
| `%vaultcrates_milestone_<CrateName>_<milestoneId>%` | `claimed` / `ready` / `needs N more opens` |
| `%vaultcrates_is_animating%` | `true` / `false` |
| `%vaultcrates_active_animations%` | Active animation count server-wide |

---

## 🔧 Developer API

```java
VaultCratesAPI api = VaultCratesAPI.get();

// Virtual keys
int keys = api.getVirtualKeys(player.getUniqueId(), "MyCrate");
api.addVirtualKeys(player.getUniqueId(), "MyCrate", 5);
api.removeVirtualKeys(player.getUniqueId(), "MyCrate", 1);

// Physical keys
api.givePhysicalKey(player, crate, 3);

// Open programmatically
api.openCrate(player, api.getCrate("MyCrate"), crateLocation);

// Events
@EventHandler
public void onCrateOpened(CrateOpenedEvent e) {
    List<Reward> rewards = e.getRewards();
    Reward best = e.getBestReward();
}
```

---

## 📄 License

All rights reserved. Do not redistribute or resell without explicit permission from the author.

---

<div align="center">
Made with ❤️ by <strong>itslarss</strong>
</div>
