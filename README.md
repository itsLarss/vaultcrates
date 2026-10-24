![VaultCrates](https://raw.githubusercontent.com/itsLarss/vaultcrates/main/logo.png)

# VaultCrates

**A feature-rich, fully animated crate plugin for Paper 26.x**

No NMS · No legacy libraries · No external hologram plugin required

[![Paper](https://img.shields.io/badge/Paper-26.1%20%E2%80%93%2026.3-F96854?style=flat-square)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25+-007396?style=flat-square&logo=openjdk)](https://adoptium.net)
[![Modrinth](https://img.shields.io/modrinth/dt/vaultcrates?style=flat-square&logo=modrinth&color=00AF5C)](https://modrinth.com/plugin/vaultcrates)
[![License](https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square)](https://github.com/itsLarss/vaultcrates/blob/main/LICENSE)

---

## ✨ Features

- **12 unique animations** — particles, sounds and visual effects for every style
- **Physical & Virtual Keys** — NBT-tagged items with anti-dupe UUID tracking; virtual keys stored per player in the database
- **Pity system** — configurable guaranteed drop after N opens per rarity
- **Reward limits** — global and per-player caps per reward
- **Virtual key shop** — players buy keys with in-game money via `/vc shop` (requires Vault or VaultUnlocked)
- **Pouches** — right-clickable reward bags with configurable random number ranges
- **Holograms** — floating Text Display entities, no external plugin needed; persist across restarts
- **Placement ID system** — every placed crate gets a unique ID; remove it with `/vc remove <id>`
- **Furniture crates** — use ItemsAdder furniture as a crate, including multi-entity furniture (display + hitbox)
- **In-game GUI editor** — edit crates and keys without touching a YAML file
- **Reward preview** — paginated GUI with normalised drop percentages; toggleable per crate
- **Best Prizes** — guaranteed bonus reward rolled from a separate pool
- **Bundled items** — give multiple bonus items alongside the main reward
- **Selectable rewards** — players pick their prize from N random options
- **Milestones** — bonus rewards after X total crate opens
- **Reward footer** — auto-appended lore lines showing reward type and rarity
- **NPC support** — Citizens, FancyNpcs, ZNPCsPlus, ZNPCs, or any entity via `/vc linknpc`
- **Multi-language** — English, German, French, Spanish built in; add any language with a `messages_<code>.yml` file
- **Multi-database** — JSON (default), SQLite or MySQL with automatic table creation
- **Developer API** — custom events, programmatic crate opening, key management

---

## 📋 Requirements

| | |
|---|---|
| Server | **Paper** (or forks) 26.1.x, 26.2 or 26.3 |
| Java | **25+** |

### Which jar do I need?

| Your server | Download |
|---|---|
| 26.1 – 26.1.x | `VaultCrates-1.1.0-26.1.x.jar` |
| 26.2.x | `VaultCrates-1.1.1-26.2.jar` |
| 26.3.x | `VaultCrates-1.1.2-26.3.jar` |

Each jar refuses to load on a Minecraft version older than the one it was built for.
For Minecraft 1.21.x, use VaultCrates **1.0.1**.

### Optional soft-dependencies

| Plugin | Tested API version | Purpose |
|---|---|---|
| [Vault](https://www.spigotmc.org/resources/vault.34315/) / [VaultUnlocked](https://modrinth.com/plugin/vaultunlocked) + economy plugin | VaultUnlocked 2.20 | Key shop, economy-priced crates |
| [PlaceholderAPI](https://modrinth.com/plugin/placeholderapi) | 2.12.3 | Placeholders in messages & holograms |
| [ItemsAdder](https://www.spigotmc.org/resources/itemsadder.73355/) | 4.0.18 | Custom items, crate blocks & furniture, `:icon:` font images |
| [Oraxen](https://www.spigotmc.org/resources/oraxen.72448/) | 1.220 | Custom block & item models |
| [Nexo](https://www.spigotmc.org/resources/nexo.112733/) | 1.29 | Custom block & item models |
| [ExecutableItems](https://www.spigotmc.org/resources/custom-items-textures-executable-items.77578/) | SCore 5.25 | Custom items as rewards |
| [MMOItems](https://www.spigotmc.org/resources/mmoitems-premium.39267/) | 6.10 | Custom items as rewards |
| [Citizens](https://www.spigotmc.org/resources/citizens.13811/) | 2.0.44 | NPC-to-crate linking |
| [FancyNpcs](https://modrinth.com/plugin/fancynpcs) | 2.11 | NPC-to-crate linking |
| [ZNPCsPlus](https://modrinth.com/plugin/znpcsplus) | 2.0 | NPC-to-crate linking |
| [ZNPCs](https://www.spigotmc.org/resources/znpcs.80940/) | legacy | NPC-to-crate linking |

> All soft-dependencies are loaded via **reflection** — VaultCrates runs without any of them installed.

---

## 🚀 Installation

1. Pick the jar for your Minecraft version (see table above) and drop it into `plugins/`
2. Start or restart the server — example configs are generated automatically
3. Get a placeable crate: `/vc getcrate ExampleRound`
4. Place it anywhere — the hologram appears and the placement is saved automatically
5. Give yourself a key: `/vc key give <name> ExampleRound 1`
6. Right-click the crate to open it

---

## 💬 Commands

All commands use `/vc` (aliases: `/vaultcrates`, `/crate`).

### Crate management

| Command | Permission | Description |
|---|---|---|
| `/vc getcrate <crate>` | `vaultcrates.admin` | Get the placeable crate item |
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
| `/vc remove <id>` | `vaultcrates.admin` | Remove a placed crate by its ID |

> **Tip:** The placement ID is shown in chat when you place a crate.
> **Block crates:** Shift + left-click breaks and removes the block (requires `vaultcrates.admin`).
> **Furniture crates:** Shift + left-click only shows the ID — remove them with `/vc remove <id>`, so nobody deletes a crate by accident.

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
| `/vc linknpc <crate>` | `vaultcrates.linknpc` | Link the nearest entity or Citizens NPC (stand within 3 blocks) |
| `/vc linknpc <crate> fancynpcs <name>` | `vaultcrates.linknpc` | Link a FancyNpcs NPC by its name |
| `/vc linknpc <crate> znpcsplus <id>` | `vaultcrates.linknpc` | Link a ZNPCsPlus NPC by its id |
| `/vc linknpc <crate> znpcs <name>` | `vaultcrates.linknpc` | Link a ZNPCs NPC by its name |

FancyNpcs and ZNPCsPlus NPCs are packet-based and have no real entity, so they are linked by name/id.
Opening a crate through an NPC requires a key, just like the crate itself.

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
  Crate_Radius: 3     # Min. distance between two crates, 0 = off

Keys:
  Virtual_Keys_Enabled: true
  Physical_Keys_Enabled: true

Shop:
  Enabled: true
  VirtualKeys: true   # true = virtual keys; false = physical key items

Win_Message: "&8[&6VaultCrates&8] &7You won {reward_name} &8[{rarity}&8] &7from &e{crate_name}&7!"

RewardFooter:         # Extra lore lines appended to every reward item
  Enabled: false
  Fallback: "Reward"
  Lines:
    - ""
    - "&8Reward Type » &7{reward_type}"
    - "&8Rarity » &7{rarity_name}"
```

### Crate config (`crates/MyCrate.yml`)

```yaml
Animation: ROUND          # See animations list
Material: ENDER_CHEST
IAModel: "namespace:my_crate"   # Optional — ItemsAdder block or furniture
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
    Rarity: epic           # common | rare | epic | legendary | jackpot | mega_jackpot | custom
    Chance: 10.0           # Relative weight — does not need to sum to 100
    Material: DIAMOND
    Amount: 1
    Glow: true             # Visual glint only — the item can still be enchanted normally
    GiveItem: true
    Type: "Item"           # Optional — shown in RewardFooter as {reward_type}
    Enchantments:
      sharpness: 5
      "myplugin:soulbound": 1   # Enchantments from enchant plugins via namespaced id
    Commands: []
    EnchantCommands: []    # Run after the item is given (for enchant plugins without registry enchants)
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
| `common` | Common | Gray |
| `rare` | Rare | Green |
| `epic` | Epic | Purple |
| `legendary` | **Legendary** | Gold |
| `jackpot` | **Jackpot** | Red |
| `mega_jackpot` | **✦ Mega Jackpot ✦** | Dark red |

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
| `SQLITE` | File-based SQL. Bundled driver — no extra jar needed. |
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
| `CONTRABAND` | Items float upward and vanish one by one |
| `INSTANT` | No animation — particle burst, reward given immediately |
| `AIR_STRIKE` | Items fall from height with lightning strikes |
| `BREAKOUT` | Items burst outward and arc back |
| `METEOR_SHOWER` | Items rain down with flame trails |
| `YIN_YANG` | Two rings converge in a yin-yang pattern |

---

## 🎨 Custom item support

```yaml
# In a reward:
Material:     "namespace:item_id"   # ItemsAdder item
OraxenModel:  "item_id"
NexoModel:    "item_id"
EIModel:      "my_item_id"          # ExecutableItems
MMOItem:      "TYPE:item_id"        # e.g. SWORD:my_sword

# On a crate or key:
IAModel:      "namespace:item_id"
OraxenModel:  "item_id"
NexoModel:    "item_id"
```

ItemsAdder font images (`:icon_name:`) work in every text field: names, lore, holograms and messages.

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

## 🛠️ Building from source

Requires JDK 25 and Maven.

```bash
mvn package -P mc-26.1   # → target/VaultCrates-1.1.0-26.1.x.jar
mvn package -P mc-26.2   # → target/VaultCrates-1.1.1-26.2.jar
mvn package -P mc-26.3   # → target/VaultCrates-1.1.2-26.3.jar
```

---

## 📄 License

VaultCrates is free software licensed under the **GNU General Public License v3.0**.
You may use, modify and redistribute it under the terms of the GPL-3.0 — modified versions must stay open source under the same license.
See [LICENSE](https://github.com/itsLarss/vaultcrates/blob/main/LICENSE) for the full text.

---

Made with ❤️ by **itslarss**
