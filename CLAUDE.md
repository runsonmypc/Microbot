# Rs2 API Reference Guide for Microbot Plugin Development

## Core Principle
**ALWAYS use existing Rs2 API methods instead of creating custom implementations.** The Rs2 API provides comprehensive, tested, and optimized methods for all common game interactions. Before implementing any custom logic, check if an Rs2 API method already exists for your use case.

## Quick Reference

### Rs2Bank - Banking Operations
- `openBank()` - Opens nearest bank automatically
- `depositAll(id)`, `depositOne(id)`, `depositX(id, amount)` - Deposit items
- `depositAllExcept(ids/names)` - Smart depositing with exceptions
- `withdrawOne(id/name)`, `withdrawAll(id/name)`, `withdrawX(id, amount)` - Withdraw items
- `withdrawAndEquip(id)` - Withdraw and immediately equip
- `isOpen()`, `closeBank()` - Bank state management
- `walkToBank()`, `getNearestBank()` - Bank navigation
- `handleBankPin(pin)` - Automatic PIN handling

### Rs2Inventory - Inventory Management
- `contains(id/name)` - Check item presence
- `count(id)` - Count specific items
- `isFull()`, `isEmpty()`, `emptySlotCount()` - Inventory state
- `interact(id, action)` - Interact with items
- `combine(id1, id2)` - Combine items
- `drop(id)`, `dropAll()` - Drop items
- `all()`, `all(filter)` - Get filtered item lists
- `get(id/name)` - Get specific items

### Rs2Combat - Combat Control
- `inCombat()` - Check combat status
- `setAttackStyle(style)` - Change attack style
- `enableAutoRetaliate()`, `setAutoRetaliate(state)` - Auto retaliate control
- `getSpecState()`, `setSpecState(state, energy)` - Special attack management

### Rs2Player - Player State
- `isWalking()`, `waitForWalking()` - Movement states
- `isAnimating()` - Animation detection
- `eatAt(percentage)` - Auto-eating at health threshold
- `isFullHealth()` - Health checks
- `getWorldLocation()` - Position tracking
- `hasAntiFireActive()`, `hasAntiVenomActive()` - Potion status
- `hasRangingPotionActive()`, `hasDivineRangedActive()` - Buff tracking
- `logout()`, `logoutIfPlayerDetected(amount, time, distance)` - Safety features
- `isInMulti()` - Multi-combat detection

### Rs2GameObject - Object Interaction
- `exists(id)` - Check object existence
- `interact(id, action, distance)` - Interact with objects
- `findObjectById(id)`, `findObjectByLocation(point)` - Find objects
- `findBank()`, `findChest()`, `findDoor(id)` - Find specific objects
- `hasLineOfSight(object)` - Visibility checks
- `getGameObjects()`, `getGroundObjects()`, `getWallObjects()` - Get object lists

### Rs2Npc - NPC Interaction
- `getNpcs()` - Stream of all NPCs sorted by distance
- `interact(npc, action)` - Perform NPC actions
- `attack(npc)` - Initiate combat
- `hasLineOfSight(npc)` - Check visibility
- `getNpcsForPlayer()` - NPCs interacting with player
- `getHealth(npc)` - Calculate NPC health
- `validateInteractable(npc)` - Ensure NPC can be interacted with

### Rs2Prayer - Prayer Management
- `toggle(prayer, state)` - Enable/disable prayers
- `isPrayerActive(prayer)` - Check prayer status
- `isQuickPrayerEnabled()` - Quick prayer status
- `isOutOfPrayer()` - Prayer point check

### Rs2Equipment - Equipment Management
- `isWearing(id/name)` - Check if item is equipped
- `hasEquippedSlot(slot)` - Check slot occupancy
- `getEquippedItem(slot)` - Get item in specific slot
- `useAmuletAction(location)`, `useRingAction(location)` - Jewelry actions
- `isWearingFullGuthan()` - Set checks

### Rs2Camera - Camera Control
*(Check Rs2Camera.md for methods)*

### Rs2Dialogue - Dialogue Handling
*(Check Rs2Dialogue.md for methods)*

### Rs2Food - Food Management
*(Check Rs2Food.md for methods)*

### Rs2GroundItem - Ground Item Interaction
*(Check Rs2GroundItem.md for methods)*

### Rs2Keyboard - Keyboard Input
*(Check Rs2Keyboard.md for methods)*

### Rs2Magic - Magic Spells
*(Check Rs2Magic.md for methods)*

### Rs2MiniMap - Minimap Operations
*(Check Rs2MiniMap.md for methods)*

### Rs2Walker - Walking/Pathfinding
*(Check Rs2Walker.md for methods)*

### Rs2Widget - Widget Interaction
*(Check Rs2Widget.md for methods)*

## Best Practices

### 1. Method Discovery
Before implementing any functionality:
1. Check relevant Rs2 API classes
2. Look for existing methods that match your needs
3. Use API methods even if they seem slightly more complex than custom code

### 2. Common Patterns

#### Banking Pattern
```java
// GOOD - Using Rs2 API
if (!Rs2Bank.isOpen()) {
    Rs2Bank.openBank();
}
Rs2Bank.depositAllExcept("Lobster", "Shark");
Rs2Bank.withdrawX(ItemID.LOBSTER, 10);

// BAD - Custom implementation
// Don't write custom banking logic
```

#### Combat Pattern
```java
// GOOD - Using Rs2 API
if (!Rs2Combat.inCombat()) {
    NPC target = Rs2Npc.getNpcs()
        .filter(npc -> npc.getName().equals("Guard"))
        .findFirst()
        .orElse(null);
    if (target != null) {
        Rs2Npc.attack(target);
    }
}
```

#### Inventory Pattern
```java
// GOOD - Using Rs2 API
if (Rs2Inventory.contains("Food") && Rs2Player.getHealthPercentage() < 50) {
    Rs2Inventory.interact("Food", "Eat");
}

// Check inventory space
if (Rs2Inventory.isFull()) {
    Rs2Bank.openBank();
    Rs2Bank.depositAll();
}
```

### 3. State Checking
Always use Rs2 API state checks:
- `Rs2Bank.isOpen()` instead of checking widgets manually
- `Rs2Combat.inCombat()` instead of checking animations
- `Rs2Inventory.isFull()` instead of counting slots
- `Rs2Player.isWalking()` instead of checking movement flags

### 4. Error Handling
Rs2 API methods include built-in error handling and null checks. Trust the API to handle edge cases properly.

### 5. Sleeping and Waiting
Use Rs2 API's built-in waiting mechanisms:
- `Rs2Player.waitForWalking()` - Waits for walking to complete
- Methods like `openBank()` include appropriate sleeps

## Performance Considerations

1. **Stream Operations**: Methods like `Rs2Npc.getNpcs()` return streams - use efficiently
2. **Caching**: Some Rs2 classes cache data (e.g., Rs2Bank maintains local bank cache)
3. **Thread Safety**: Rs2 API handles client thread operations safely

## API Evolution

The Rs2 API is continuously improved. When in doubt:
1. Check the actual implementation in `/runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/`
2. Look for newer methods that might replace deprecated ones
3. Follow existing plugin patterns in the codebase

## Remember

- **Never recreate existing Rs2 functionality**
- **Always check Rs2 API first before custom implementation**
- **Use Rs2 API methods even for simple operations**
- **Trust the API's error handling and state management**
- **Follow the patterns established by the Rs2 API**

This approach ensures:
- Consistency across plugins
- Reduced bugs from custom implementations
- Easier maintenance and updates
- Better performance through optimized API methods