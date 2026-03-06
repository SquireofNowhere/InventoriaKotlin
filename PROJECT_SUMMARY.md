# 📋 Inventoria Android - Complete Project Summary

## 🎯 Project Overview

**Inventoria** is a professional-grade Android inventory management application. It uniquely combines physical hierarchy (nested storage) with logical relationships (linked groups) to create a highly flexible asset tracking system.

## ✨ Core Innovations

### 1. Hybrid Movement Engine
- **Physical Hierarchy**: Items can be nested inside containers (bags, boxes, toolkits).
- **Logical Linking**: Items can be linked to follow each other. If a "leader" moves, the entire "follower group" moves with it.
- **Conflict Resolution**: Smart protection logic ensures that physical storage always takes priority over logical links to prevent recursive loops or accidental "ejections."

### 2. Intelligent Sync & Recovery
- **Monotonic Sync**: Every operation uses a strictly increasing timestamp, guaranteeing that rapid actions (like starting/stopping a task in the same millisecond) sync in the correct order.
- **Self-Healing Data**: The repository automatically detects and repairs circular dependencies or self-parenting loops during sync.
- **Tab-Triggered Refresh**: Triggers a full cloud-sync every time the user switches between main app sections.

### 3. Tactile Performance UI
- **Unified Tree View**: The deep hierarchical structure from the main inventory is preserved inside individual Collections.
- **Interactive Drag-and-Drop**: Context-aware drag actions. Dragging a container onto another container prompts for "Store" vs "Link."
- **Premium Haptics**: Integrated tactile feedback for all major state transitions and drag operations.

## 📁 File Structure

```
com.inventoria.app/
├── data/
│   ├── local/          # Room Persistence (Inventory, Collection, Task)
│   ├── model/          # Domain Entities (lastParentId & updatedAt support)
│   └── repository/     # Business Logic (Location, Sync, Linked Groups)
├── di/                 # Dependency Injection (Hilt)
└── ui/
    ├── main/           # App Entry & Tab-Sync Navigation
    ├── screens/        # Dashboard, Inventory, Collections, Tasks, Map
    └── theme/          # Purple Sheen Design System
```

## 📊 Features Implemented

### ✅ Core Features
- **Firebase Bi-Sync**: Real-time multi-device data parity.
- **Equip/Repack**: Context-aware un-equipping that remembers original storage locations.
- **Task Tracker**: Segmented session tracking with productivity scoring.
- **Interactive Map**: Global visualization of item and user locations.

---
**Version 1.15 (Stable)**
**Built with 💜 using Kotlin, Jetpack Compose, Hilt, and Room.**
