# Farming Contract Plugin

Automates Farming Guild contracts in Old School RuneScape, handling everything from contract acquisition to crop harvesting and turn-in.

## Features

- **Automatic Contract Management**: Gets contracts from Guildmaster Jane based on your farming level
- **Smart Patch Detection**: Identifies correct farming patches and their current state
- **Intelligent Seed Preparation**: Only banks for seeds when needed (skips if crop already growing)
- **Auto-Downgrade Support**: Automatically requests easier contracts when seeds/saplings unavailable
- **Complete Automation**: Handles planting, composting, harvesting, and clearing
- **Multi-Patch Support**: Works with all contract types (herbs, allotments, flowers, bushes, trees, fruit trees, cacti)
- **Contract Completion Detection**: Monitors chat messages to detect when contracts are completed
- **Anti-Spam Protection**: Prevents re-clicking while walking or animating

## Requirements

- Access to the Farming Guild (45 Farming minimum)
- Appropriate farming level for desired contract tier
- Required seeds (or seed packs to open)
- Basic farming tools (rake, seed dibber, spade)
- (Optional) Compost for better yields
- (Optional) Plant cure for diseased crops
- (Optional) Magic secateurs for increased herb/allotment yield
- (Optional) 200gp per tree/fruit tree for clearing

## Configuration

### Contract Tier
- **Automatic**: Selects tier based on your farming level
- **Easy**: Level 45-65 contracts
- **Medium**: Level 65-85 contracts  
- **Hard**: Level 85+ contracts

### Auto-Downgrade
- **Auto Downgrade**: When enabled, automatically requests easier contracts if seeds/saplings are unavailable
- The plugin will continue downgrading until it finds a contract with available seeds or reaches Easy tier

### Compost Settings
- **Use Compost**: Enable/disable automatic composting
- **Compost Type**: Regular, Supercompost, or Ultracompost

## How It Works

1. **Initialize**: Checks with Jane for existing contracts
2. **Get Contract**: Obtains appropriate tier contract from Jane
3. **Check Patch**: Examines the target patch to determine its state
4. **Prepare**: Banks for necessary tools and seeds (skips if crop already growing)
   - If seeds unavailable and auto-downgrade enabled, requests easier contract
5. **Farm**: Travels to patch and performs required actions:
   - Clears weeds if needed
   - Plants seeds
   - Applies compost (if configured)
   - Harvests ready crops
   - Performs check-health for trees/bushes
   - Clears dead plants
6. **Complete**: Returns to Jane for reward and new contract

## Patch States

The plugin recognizes and handles these patch states:
- **EMPTY**: Ready for planting
- **GROWING**: Crop is growing (waits or stops)
- **HARVESTABLE**: Ready to harvest
- **UNCHECKED**: Needs check-health (trees/bushes/cacti)
- **DEAD**: Needs clearing
- **DISEASED**: Needs plant cure
- **STUMP**: Tree stump needs clearing

## Special Handling

### Bushes & Cacti
After check-health completes the contract, continues to:
1. Harvest produce (Pick/Pick-spine) 
2. Clear the patch (requires spade)
3. Only then get a new contract

### Trees & Fruit Trees
1. Performs check-health to complete contract
2. Pays nearby gardener 200gp to remove tree
3. Gets new contract

### Allotments
- Plants 3 seeds as required
- Handles both North and South patches in guild

## Banking Logic

The plugin intelligently manages banking:
- Deposits everything except tools and coins
- Withdraws exact amounts needed:
  - 1 of each tool (rake, spade, seed dibber)
  - Correct number of seeds (1 for most, 3 for allotments)
  - Compost if configured
  - Plant cure if available
- Opens seed packs automatically
- Notes harvested crops when inventory is full

## Safety Features

- **Animation Checks**: Won't spam click while animating
- **Movement Checks**: Waits for walking to complete before acting
- **Contract Validation**: Verifies contract status with Jane
- **State Verification**: Double-checks patch states before actions
- **Error Recovery**: Handles unexpected states gracefully

## Known Locations

The plugin knows all Farming Guild patch locations:
- **Herb**: Southeast area
- **Allotment North**: North section
- **Allotment South**: South section
- **Flower**: Near allotments
- **Bush**: West side
- **Tree**: Northwest corner
- **Fruit Tree**: West side
- **Cactus**: North area (coordinates: 1264-1265, 3747-3748)

## Troubleshooting

### Plugin won't start
- Ensure you're in or near the Farming Guild
- Check that you have the required farming level

### Can't find patch
- Make sure you're logged into a members world
- Verify you have access to the Farming Guild

### Banking issues
- Ensure you have the required seeds in bank
- Check that seed packs are being opened
- Verify tools are available
- If auto-downgrade is enabled, plugin will request easier contracts when seeds unavailable

### Contract not completing
- Check chat messages for completion notification
- Ensure check-health was performed for trees/bushes
- Verify the correct crop was planted

## Tips

- Keep a stack of each seed type in bank for efficiency
- Use seed packs for convenient storage
- Maintain a supply of ultracompost for best yields
- Have plant cure ready for diseased crops
- Keep 200gp in inventory for tree clearing
- Enable auto-downgrade to automatically get easier contracts when seeds are unavailable

## Updates & Improvements

Recent improvements include:
- Added auto-downgrade feature for unavailable seeds/saplings
- Fixed bush/cactus/herb harvesting to ensure spade is obtained when patch already has harvestable crops
- Fixed seed pack opening to always occur, even when skipping seed preparation for existing crops
- Fixed state persistence between contracts preventing stale patch states
- Fixed spam clicking during movement
- Added chat-based contract completion detection
- Improved seed preparation logic (skips if crop already growing)
- Enhanced bush/cactus handling for complete clearing
- Prevented duplicate planting after contract completion
- Optimized pathfinding using built-in Rs2 API methods