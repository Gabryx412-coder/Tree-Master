package com.gabry.treemaster;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class TreeMaster extends JavaPlugin implements Listener, CommandExecutor {

    // --- Plugin Configuration & Data ---
    private File playersConfigFile;
    private YamlConfiguration playersConfig;

    // Maps to store player preferences for tree breaking and stripping functions
    private final Map<UUID, Boolean> breakEnabled = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> stripEnabled = new ConcurrentHashMap<>();

    // NEW: Map to store player-placed log block locations
    // Key: Player UUID, Value: Set of block locations (formatted as "world_x_y_z")
    private final Map<UUID, Set<String>> placedLogBlocks = new ConcurrentHashMap<>();

    // Cooldown map to prevent spamming tree felling
    private final Map<UUID, Long> breakCooldowns = new ConcurrentHashMap<>();
    private final long BREAK_COOLDOWN_MILLIS = 1000; // 1 second cooldown

    // Cooldown map for stripping mode
    private final Map<UUID, Long> stripCooldowns = new ConcurrentHashMap<>();
    private final long STRIP_COOLDOWN_MILLIS = 1000; // 1 second cooldown

    // Messages for players, configurable
    private String prefix = "§a§lTreeMaster §7» ";
    private String enabledBreakMessage = "§aAbbattimento automatico dell'albero abilitato.";
    private String disabledBreakMessage = "§cAbbattimento automatico dell'albero disabilitato.";
    private String enabledStripMessage = "§aModalità stripping abilitata.";
    private String disabledStripMessage = "§cModalità stripping disabilitata.";
    private String noPermissionMessage = "§cNon hai il permesso per usare questo comando.";
    private String invalidCommandMessage = "§cUtilizzo: /treemaster function <break|strip> <enable|disable>";
    private String notHoldingAxeMessage = "§cDevi tenere un'ascia per usare la modalità stripping.";
    private String stripCooldownMessage = "§cDevi aspettare prima di poter riattivare la modalità stripping.";
    private String breakCooldownMessage = "§cDevi aspettare prima di poter abbattere un altro albero.";
    private String treeFelledMessage = "§aHai abbattuto un intero albero!";

    // Set of all log materials, including stripped variants and bamboo
    private final Set<Material> LOG_MATERIALS = new HashSet<>();
    private final Set<Material> STRIPPED_LOG_MATERIALS = new HashSet<>();

    // --- Plugin Lifecycle Methods ---
    @Override
    public void onEnable() {
        // Register events
        getServer().getPluginManager().registerEvents(this, this);

        // Setup configuration files
        setupFiles();
        loadPlayerPreferences(); // Carica anche i blocchi piazzati qui

        // Initialize log materials
        initializeLogMaterials();

        // Register command executor
        Objects.requireNonNull(this.getCommand("treemaster")).setExecutor(this);

        getLogger().log(Level.INFO, prefix + "Plugin abilitato con successo!");
    }

    @Override
    public void onDisable() {
        savePlayerPreferences(); // Salva anche i blocchi piazzati qui
        getLogger().log(Level.INFO, prefix + "Plugin disabilitato con successo!");
    }

    // --- File Management ---
    private void setupFiles() {
        playersConfigFile = new File(getDataFolder(), "players.yml");
        if (!playersConfigFile.exists()) {
            playersConfigFile.getParentFile().mkdirs(); // Crea le directory parenti se non esistono
            try {
                playersConfigFile.createNewFile();
                getLogger().log(Level.INFO, "players.yml creato con successo.");
            } catch (IOException e) {
                getLogger().log(Level.SEVERE, "Impossibile creare players.yml: " + e.getMessage(), e);
            }
        }
        playersConfig = new YamlConfiguration();
        try {
            playersConfig.load(playersConfigFile);
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, "Impossibile caricare players.yml: " + e.getMessage(), e);
        }
    }

    private void loadPlayerPreferences() {
        try {
            playersConfig.load(playersConfigFile);
            for (String uuidString : playersConfig.getKeys(false)) {
                try {
                    UUID playerUUID = UUID.fromString(uuidString);
                    breakEnabled.put(playerUUID, playersConfig.getBoolean(uuidString + ".break", true));
                    stripEnabled.put(playerUUID, playersConfig.getBoolean(uuidString + ".strip", false));

                    // NEW: Load placed log blocks for each player
                    List<String> placedBlocksList = playersConfig.getStringList(uuidString + ".placed_logs");
                    if (placedBlocksList != null && !placedBlocksList.isEmpty()) {
                        placedLogBlocks.put(playerUUID, new HashSet<>(placedBlocksList));
                    } else {
                        placedLogBlocks.put(playerUUID, new HashSet<>());
                    }

                } catch (IllegalArgumentException e) {
                    getLogger().log(Level.WARNING, "UUID non valido trovato in players.yml: " + uuidString);
                }
            }
            getLogger().log(Level.INFO, "Preferenze dei giocatori e blocchi piazzati caricati.");
        } catch (IOException | InvalidConfigurationException e) {
            getLogger().log(Level.SEVERE, "Errore durante il caricamento delle preferenze dei giocatori e blocchi piazzati: " + e.getMessage(), e);
        }
    }

    private void savePlayerPreferences() {
        for (Map.Entry<UUID, Boolean> entry : breakEnabled.entrySet()) {
            playersConfig.set(entry.getKey().toString() + ".break", entry.getValue());
        }
        for (Map.Entry<UUID, Boolean> entry : stripEnabled.entrySet()) {
            playersConfig.set(entry.getKey().toString() + ".strip", entry.getValue());
        }
        // NEW: Save placed log blocks for each player
        for (Map.Entry<UUID, Set<String>> entry : placedLogBlocks.entrySet()) {
            playersConfig.set(entry.getKey().toString() + ".placed_logs", new ArrayList<>(entry.getValue()));
        }
        try {
            playersConfig.save(playersConfigFile);
            getLogger().log(Level.INFO, "Preferenze dei giocatori e blocchi piazzati salvati.");
        } catch (IOException e) {
            getLogger().log(Level.SEVERE, "Errore durante il salvataggio delle preferenze dei giocatori e blocchi piazzati: " + e.getMessage(), e);
        }
    }

    // Helper to convert Block to a unique string for saving
    private String blockLocationToString(Block block) {
        return block.getWorld().getName() + "_" + block.getX() + "_" + block.getY() + "_" + block.getZ();
    }

    // Helper to check if a block is placed by a player
    private boolean isPlayerPlaced(Block block) {
        String blockLocString = blockLocationToString(block);
        // Controlla in tutti i set di giocatori se il blocco è stato piazzato
        for (Set<String> locations : placedLogBlocks.values()) {
            if (locations.contains(blockLocString)) {
                return true;
            }
        }
        return false;
    }

    // --- Material Initialization ---
    private void initializeLogMaterials() {
        // Normal logs
        LOG_MATERIALS.add(Material.OAK_LOG);
        LOG_MATERIALS.add(Material.BIRCH_LOG);
        LOG_MATERIALS.add(Material.SPRUCE_LOG);
        LOG_MATERIALS.add(Material.JUNGLE_LOG);
        LOG_MATERIALS.add(Material.ACACIA_LOG);
        LOG_MATERIALS.add(Material.DARK_OAK_LOG);
        LOG_MATERIALS.add(Material.MANGROVE_LOG);
        LOG_MATERIALS.add(Material.CHERRY_LOG);
        LOG_MATERIALS.add(Material.BAMBOO_BLOCK); // Bamboo block is a log-like material
        LOG_MATERIALS.add(Material.CRIMSON_STEM);
        LOG_MATERIALS.add(Material.WARPED_STEM);

        // Stripped logs
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_OAK_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_BIRCH_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_SPRUCE_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_JUNGLE_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_ACACIA_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_DARK_OAK_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_MANGROVE_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_CHERRY_LOG);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_CRIMSON_STEM);
        STRIPPED_LOG_MATERIALS.add(Material.STRIPPED_WARPED_STEM);

        LOG_MATERIALS.addAll(STRIPPED_LOG_MATERIALS); // Include stripped logs in the general log set for easier checks
    }

    // --- Event Handlers ---

    /**
     * NEW: Handles BlockPlaceEvent to mark player-placed log blocks.
     *
     * @param event The BlockPlaceEvent.
     */
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        Player player = event.getPlayer();

        if (LOG_MATERIALS.contains(block.getType())) {
            // Add the block location to the player's set of placed logs
            // Ensure the set exists for the player's UUID
            placedLogBlocks.computeIfAbsent(player.getUniqueId(), k -> new HashSet<>()).add(blockLocationToString(block));
            // No need to save immediately, save happens on plugin disable.
            // If you want more persistence, you could save more often or have a periodic save task.
        }
    }


    /**
     * Handles the BlockBreakEvent to implement automatic tree felling.
     * Recognizes single logs and 2x2 large trees (Dark Oak, large Spruce).
     * Accounts for player preferences, tool enchantments (Efficiency, Unbreaking), and cooldowns.
     * NEW: Added logic to ignore automatic felling for player-placed log blocks.
     *
     * @param event The BlockBreakEvent.
     */
    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        UUID playerUUID = player.getUniqueId();

        // Check if the broken block is a log material
        if (!LOG_MATERIALS.contains(block.getType())) {
            return;
        }

        // NEW: If the broken block is player-placed, cancel auto-felling and remove it from tracking
        if (isPlayerPlaced(block)) {
            // Rimuovi il blocco da QUALSIASI set di blocchi piazzati (più robusto)
            String blockLocString = blockLocationToString(block);
            placedLogBlocks.values().forEach(set -> set.remove(blockLocString));

            // Allow the block to break normally (don't cancel the event, just skip auto-felling)
            return; // Skip the rest of the auto-felling logic
        }


        // Check if the player has the break function enabled
        if (!breakEnabled.getOrDefault(playerUUID, true)) { // Default to true if not found (new player)
            return;
        }

        // Check for cooldown
        if (breakCooldowns.containsKey(playerUUID) && (System.currentTimeMillis() - breakCooldowns.get(playerUUID) < BREAK_COOLDOWN_MILLIS)) {
            player.sendMessage(prefix + breakCooldownMessage);
            event.setCancelled(true); // Cancel the break to prevent breaking only one block
            return;
        }

        // Set cooldown
        breakCooldowns.put(playerUUID, System.currentTimeMillis());

        // Attempt to find the tree base for 2x2 trees
        Set<Block> treeBaseBlocks = findTwoByTwoTreeBase(block);

        // If it's a 2x2 tree and not all base blocks are broken, cancel and inform
        if (treeBaseBlocks != null) {
            // No need to check for all 4 being broken simultaneously in this event.
            // The logic assumes breaking any of the 4 base blocks triggers the felling.
        } else {
            // It's not a 2x2 tree, so check if it's a single log type and is the lowest block of a tree
            if (!isLowestLog(block)) {
                return; // Not the base of a single-trunk tree
            }
        }

        // --- Start tree felling logic ---
        event.setCancelled(true); // Cancel the initial block break to control the drop/give process

        // Get the tool used by the player
        ItemStack toolInHand = player.getInventory().getItemInMainHand();
        int fortuneLevel = toolInHand.getEnchantmentLevel(Enchantment.FORTUNE); // Not used in this method, but kept for context.
        int efficiencyLevel = toolInHand.getEnchantmentLevel(Enchantment.EFFICIENCY); // Not used in this method, but kept for context.
        int unbreakingLevel = toolInHand.getEnchantmentLevel(Enchantment.UNBREAKING);

        // Find all log blocks in the tree
        Set<Block> treeBlocks = new HashSet<>();
        Queue<Block> blocksToProcess = new LinkedList<>();

        // Add the initial block(s) to process. If it's a 2x2 base, add all of them.
        if (treeBaseBlocks != null && !treeBaseBlocks.isEmpty()) {
            blocksToProcess.addAll(treeBaseBlocks);
            treeBlocks.addAll(treeBaseBlocks);
        } else {
            blocksToProcess.add(block);
            treeBlocks.add(block);
        }

        // BFS to find all connected log blocks
        while (!blocksToProcess.isEmpty()) {
            Block current = blocksToProcess.poll();

            for (BlockFace face : BlockFace.values()) {
                Block relative = current.getRelative(face);
                // Only add if it's a log, not already in treeBlocks, AND NOT player-placed
                // This prevents natural tree felling from breaking player-placed logs that are part of the "natural" tree structure
                if (LOG_MATERIALS.contains(relative.getType()) && !treeBlocks.contains(relative) && !isPlayerPlaced(relative)) {
                    treeBlocks.add(relative);
                    blocksToProcess.add(relative);
                }
            }
        }

        // Remove the blocks with a slight delay and give items/drop them
        new BukkitRunnable() {
            int processedCount = 0;
            Iterator<Block> blockIterator = treeBlocks.iterator();

            @Override
            public void run() {
                if (!blockIterator.hasNext()) {
                    // All blocks processed, cancel task
                    cancel();
                    if (player.isOnline()) {
                        player.sendMessage(prefix + treeFelledMessage);
                    }
                    return;
                }

                Block currentBlock = blockIterator.next();
                Material logType = currentBlock.getType();

                // Only process if it's still a log (not already broken by another plugin or player)
                // And ensure it's not a player-placed block (double-check, though BFS should already filter)
                if (LOG_MATERIALS.contains(logType) && !isPlayerPlaced(currentBlock)) {
                    // Handle tool durability and give/drop item
                    if (player.getGameMode() != GameMode.CREATIVE) {
                        ItemStack droppedItem = new ItemStack(getAppropriateLogItem(logType), 1);
                        // Verifica se l'inventario ha spazio in modo più robusto
                        HashMap<Integer, ItemStack> remainingItems = player.getInventory().addItem(droppedItem);
                        if (!remainingItems.isEmpty()) {
                            // Se non tutto è stato aggiunto, droppa il resto nel mondo
                            for (ItemStack item : remainingItems.values()) {
                                currentBlock.getWorld().dropItemNaturally(currentBlock.getLocation(), item);
                            }
                        }

                        // Apply durability damage to the tool
                        if (isAxe(toolInHand.getType())) {
                            ItemMeta meta = toolInHand.getItemMeta();
                            if (meta instanceof Damageable) {
                                Damageable damageable = (Damageable) meta;
                                int damage = 1;
                                if (unbreakingLevel > 0) {
                                    if (Math.random() * 100 < (100.0 / (unbreakingLevel + 1))) {
                                        damageable.setDamage(damageable.getDamage() + damage);
                                    }
                                } else {
                                    damageable.setDamage(damageable.getDamage() + damage);
                                }

                                if (damageable.getDamage() >= toolInHand.getType().getMaxDurability()) {
                                    player.getInventory().setItemInMainHand(null); // Tool breaks
                                    player.sendMessage(prefix + "§cLa tua ascia si è rotta!");
                                } else {
                                    toolInHand.setItemMeta(meta);
                                }
                            }
                        }
                    }

                    // Remove the block
                    currentBlock.setType(Material.AIR);
                }
                processedCount++;
            }
        }.runTaskTimer(this, 0, 1); // Run every tick for a smooth breakdown animation
    }

    /**
     * Helper method to check if a Material is an axe.
     * @param material The material to check.
     * @return true if the material is an axe, false otherwise.
     */
    private boolean isAxe(Material material) {
        return material.name().endsWith("_AXE");
    }

    /**
     * Determines the appropriate log item (e.g., OAK_LOG for OAK_LOG or STRIPPED_OAK_LOG).
     *
     * @param brokenLogType The material of the broken log block.
     * @return The material of the item to drop/give.
     */
    private Material getAppropriateLogItem(Material brokenLogType) {
        switch (brokenLogType) {
            case OAK_LOG:
            case STRIPPED_OAK_LOG:
                return Material.OAK_LOG;
            case BIRCH_LOG:
            case STRIPPED_BIRCH_LOG:
                return Material.BIRCH_LOG;
            case SPRUCE_LOG:
            case STRIPPED_SPRUCE_LOG:
                return Material.SPRUCE_LOG;
            case JUNGLE_LOG:
            case STRIPPED_JUNGLE_LOG:
                return Material.JUNGLE_LOG;
            case ACACIA_LOG:
            case STRIPPED_ACACIA_LOG:
                return Material.ACACIA_LOG;
            case DARK_OAK_LOG:
            case STRIPPED_DARK_OAK_LOG:
                return Material.DARK_OAK_LOG;
            case MANGROVE_LOG:
            case STRIPPED_MANGROVE_LOG:
                return Material.MANGROVE_LOG;
            case CHERRY_LOG:
            case STRIPPED_CHERRY_LOG:
                return Material.CHERRY_LOG;
            case CRIMSON_STEM:
            case STRIPPED_CRIMSON_STEM:
                return Material.CRIMSON_STEM;
            case WARPED_STEM:
            case STRIPPED_WARPED_STEM:
                return Material.WARPED_STEM;
            case BAMBOO_BLOCK:
                return Material.BAMBOO_BLOCK;
            default:
                return brokenLogType; // Should not happen if LOG_MATERIALS is correctly defined
        }
    }


    /**
     * Checks if the given block is the lowest log block in its vertical column.
     * This helps in identifying the base of a single-trunk tree.
     *
     * @param block The block to check.
     * @return True if it's the lowest log, false otherwise.
     */
    private boolean isLowestLog(Block block) {
        if (!LOG_MATERIALS.contains(block.getType())) {
            return false;
        }
        Block below = block.getRelative(BlockFace.DOWN);
        return !LOG_MATERIALS.contains(below.getType());
    }

    /**
     * Attempts to find the 2x2 base of a large tree (e.g., Dark Oak, large Spruce).
     * This method looks for a 2x2 square of log blocks at the same Y-level, where the given block is one of them.
     * It checks if the block and its neighbors form a valid 2x2 base structure.
     *
     * @param initialBlock The block that was broken.
     * @return A set of the 4 base blocks if a 2x2 tree is detected, otherwise null.
     */
    private Set<Block> findTwoByTwoTreeBase(Block initialBlock) {
        if (!LOG_MATERIALS.contains(initialBlock.getType())) {
            return null;
        }

        // Define potential relative coordinates for the 2x2 base
        // (0,0,0) is initialBlock
        List<Vector> potentialBaseOffsets = new ArrayList<>();
        potentialBaseOffsets.add(new Vector(0, 0, 0)); // The initial block itself
        potentialBaseOffsets.add(new Vector(1, 0, 0)); // x+1
        potentialBaseOffsets.add(new Vector(0, 0, 1)); // z+1
        potentialBaseOffsets.add(new Vector(1, 0, 1)); // x+1, z+1

        // Check all 4 possible orientations of a 2x2 base relative to the initial block
        for (int i = 0; i < 4; i++) { // 0: (0,0), 1: (-1,0), 2: (0,-1), 3: (-1,-1)
            int dx = (i == 1 || i == 3) ? -1 : 0;
            int dz = (i == 2 || i == 3) ? -1 : 0;

            Set<Block> candidateBase = new HashSet<>();
            boolean isBase = true;

            for (Vector offset : potentialBaseOffsets) {
                Block currentCandidate = initialBlock.getRelative(dx + offset.getBlockX(), 0, dz + offset.getBlockZ());
                if (!LOG_MATERIALS.contains(currentCandidate.getType()) || !isLowestLog(currentCandidate)) {
                    isBase = false;
                    break;
                }
                candidateBase.add(currentCandidate);
            }

            if (isBase && candidateBase.size() == 4) {
                // Found a valid 2x2 base
                return candidateBase;
            }
        }
        return null;
    }


    /**
     * Handles the PlayerInteractEvent to implement automatic tree stripping.
     * Activates stripping mode if the player interacts with a log while holding an axe.
     * IMPORTANT: Added logic to prevent stripping when placing blocks.
     * Corrected BFS for stripping to focus on connected logs.
     *
     * @param event The PlayerInteractEvent.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        UUID playerUUID = player.getUniqueId();

        // Ensure it's a right-click on a block with an item in hand
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK || block == null) {
            return;
        }

        // Check if the player has the strip function enabled
        if (!stripEnabled.getOrDefault(playerUUID, false)) { // Default to false
            return;
        }

        // --- NUOVO CONTROLLO: Evita lo stripping quando il giocatore sta piazzando un blocco ---
        // Ottieni l'item nella mano che ha attivato l'evento
        ItemStack usedItem = event.getItem(); // Questo metodo restituisce l'ItemStack usato nell'interazione
        // Se l'item usato è nullo o è aria, esci (non dovrebbe accadere con click destro su blocco)
        if (usedItem == null || usedItem.getType() == Material.AIR) {
            return;
        }

        // Se l'item nella mano **non** è un'ascia E l'item è un blocco piazzabile,
        // allora il giocatore sta probabilmente cercando di piazzare un blocco.
        // In questo caso, fermiamo il processo di stripping.
        if (!isAxe(usedItem.getType()) && usedItem.getType().isBlock()) {
            return;
        }
        // --- FINE NUOVO CONTROLLO ---


        // Check if the player is holding an axe
        if (!isAxe(usedItem.getType())) {
            player.sendMessage(prefix + notHoldingAxeMessage);
            return;
        }

        // Check for cooldown
        if (stripCooldowns.containsKey(playerUUID) && (System.currentTimeMillis() - stripCooldowns.get(playerUUID) < STRIP_COOLDOWN_MILLIS)) {
            player.sendMessage(prefix + stripCooldownMessage);
            return;
        }

        // Check if the clicked block is a log and not already stripped
        if (!LOG_MATERIALS.contains(block.getType()) || STRIPPED_LOG_MATERIALS.contains(block.getType())) {
            return; // Already stripped or not a log for stripping
        }

        // Set cooldown *prima* di eseguire la logica di stripping completa
        stripCooldowns.put(playerUUID, System.currentTimeMillis());

        // Find all log blocks in the tree for stripping
        Set<Block> treeLogs = new HashSet<>();
        Queue<Block> blocksToProcess = new LinkedList<>();
        blocksToProcess.add(block);
        treeLogs.add(block);

        // Define faces for stripping (Up, Down, and horizontal faces for 2x2 logs)
        // We want to find logs directly connected in any direction, but not necessarily through leaves or complex structures.
        // For stripping, a simple BFS is usually fine, but let's be explicit with faces for clarity.
        BlockFace[] relevantFaces = {
                BlockFace.UP, BlockFace.DOWN,
                BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
        };

        while (!blocksToProcess.isEmpty()) {
            Block current = blocksToProcess.poll();

            for (BlockFace face : relevantFaces) { // Use relevantFaces for stripping
                Block relative = current.getRelative(face);
                // Ensure it's a log material (normal or stripped), not already in the set, and not player-placed (optional, but good for consistency)
                // For stripping, we usually only care about UNSTRIPPED logs for the initial check to add to the queue,
                // but LOG_MATERIALS also contains stripped, so it's fine.
                // The crucial part is to only add logs that are NOT already stripped.
                if (LOG_MATERIALS.contains(relative.getType()) && !STRIPPED_LOG_MATERIALS.contains(relative.getType()) && !treeLogs.contains(relative)) {
                    treeLogs.add(relative);
                    blocksToProcess.add(relative);
                }
            }
        }

        // Apply stripping to all found logs
        // Collect blocks to be stripped first to avoid ConcurrentModificationException if we iterate and modify.
        List<Block> logsToStrip = new ArrayList<>();
        for (Block logBlock : treeLogs) {
            // Only strip if it's a normal log (not already stripped)
            if (LOG_MATERIALS.contains(logBlock.getType()) && !STRIPPED_LOG_MATERIALS.contains(logBlock.getType())) {
                logsToStrip.add(logBlock);
            }
        }

        if (logsToStrip.isEmpty()) {
            // If no logs were found to strip (e.g., clicked on an already stripped log, or no connected logs),
            // we might want to refund the cooldown or send a message. For now, let's just return.
            // However, the initial check `!LOG_MATERIALS.contains(block.getType()) || STRIPPED_LOG_MATERIALS.contains(block.getType())`
            // should already handle the "already stripped" case. This `if` might indicate an issue with BFS finding logs.
            return; // No unstripped logs found in the connected tree section
        }

        for (Block logBlock : logsToStrip) {
            Material strippedMaterial = getStrippedMaterial(logBlock.getType());
            if (strippedMaterial != null) {
                logBlock.setType(strippedMaterial);
            }
        }

        // Apply durability damage to the axe
        if (player.getGameMode() != GameMode.CREATIVE) {
            ItemMeta meta = usedItem.getItemMeta();
            if (meta instanceof Damageable) {
                Damageable damageable = (Damageable) meta;
                // Damage based on the number of logs ACTUALLY stripped
                int damage = logsToStrip.size();
                int unbreakingLevel = usedItem.getEnchantmentLevel(Enchantment.UNBREAKING);

                int actualDamage = 0;
                for (int i = 0; i < damage; i++) {
                    if (unbreakingLevel > 0) {
                        if (Math.random() * 100 < (100.0 / (unbreakingLevel + 1))) {
                            actualDamage++;
                        }
                    } else {
                        actualDamage++;
                    }
                }

                damageable.setDamage(damageable.getDamage() + actualDamage);

                if (damageable.getDamage() >= usedItem.getType().getMaxDurability()) {
                    if (event.getHand() == EquipmentSlot.HAND) {
                        player.getInventory().setItemInMainHand(null);
                    } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                        player.getInventory().setItemInOffHand(null);
                    }
                    player.sendMessage(prefix + "§cLa tua ascia si è rotta!");
                } else {
                    usedItem.setItemMeta(meta);
                }
            }
        }
    }

    /**
     * Converts a normal log material to its stripped counterpart.
     * This method is crucial for the stripping functionality.
     *
     * @param logMaterial The normal log material.
     * @return The stripped material, or null if no direct stripped equivalent.
     */
    private Material getStrippedMaterial(Material logMaterial) {
        switch (logMaterial) {
            case OAK_LOG:
                return Material.STRIPPED_OAK_LOG;
            case BIRCH_LOG:
                return Material.STRIPPED_BIRCH_LOG;
            case SPRUCE_LOG:
                return Material.STRIPPED_SPRUCE_LOG;
            case JUNGLE_LOG:
                return Material.STRIPPED_JUNGLE_LOG;
            case ACACIA_LOG:
                return Material.STRIPPED_ACACIA_LOG;
            case DARK_OAK_LOG:
                return Material.STRIPPED_DARK_OAK_LOG;
            case MANGROVE_LOG:
                return Material.STRIPPED_MANGROVE_LOG;
            case CHERRY_LOG:
                return Material.STRIPPED_CHERRY_LOG;
            case CRIMSON_STEM:
                return Material.STRIPPED_CRIMSON_STEM;
            case WARPED_STEM:
                return Material.STRIPPED_WARPED_STEM;
            default:
                return null; // No stripped version for BAMBOO_BLOCK or already stripped logs
        }
    }

    // --- Command Handling ---

    /**
     * Handles the '/treemaster' command.
     * Syntax: /treemaster function <break|strip> <enable|disable>
     * Requires 'treemaster.use' permission.
     * Admin commands can be added here with 'treemaster.admin' permission if needed in the future.
     *
     * @param sender The CommandSender (player or console).
     * @param command The command that was executed.
     * @param label The alias of the command used.
     * @param args The arguments passed to the command.
     * @return True if the command was handled successfully, false otherwise.
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("treemaster")) {
            return false;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(prefix + "Solo i giocatori possono usare questo comando.");
            return true;
        }

        Player player = (Player) sender;
        UUID playerUUID = player.getUniqueId();

        // Check base permission
        if (!player.hasPermission("treemaster.use")) {
            player.sendMessage(prefix + noPermissionMessage);
            return true;
        }

        // Handle sub-commands
        if (args.length == 3 && args[0].equalsIgnoreCase("function")) {
            String functionType = args[1].toLowerCase();
            String action = args[2].toLowerCase();

            boolean enable;
            if (action.equals("enable")) {
                enable = true;
            } else if (action.equals("disable")) {
                enable = false;
            } else {
                player.sendMessage(prefix + invalidCommandMessage);
                return true;
            }

            if (functionType.equals("break")) {
                breakEnabled.put(playerUUID, enable);
                player.sendMessage(prefix + (enable ? enabledBreakMessage : disabledBreakMessage));
                savePlayerPreferences(); // Save immediately
                return true;
            } else if (functionType.equals("strip")) {
                stripEnabled.put(playerUUID, enable);
                player.sendMessage(prefix + (enable ? enabledStripMessage : disabledStripMessage));
                savePlayerPreferences(); // Save immediately
                return true;
            } else {
                player.sendMessage(prefix + invalidCommandMessage);
                return true;
            }
        } else {
            player.sendMessage(prefix + invalidCommandMessage);
            return true;
        }
    }
}