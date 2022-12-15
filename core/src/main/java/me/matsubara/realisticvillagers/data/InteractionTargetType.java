package me.matsubara.realisticvillagers.data;

import me.matsubara.realisticvillagers.entity.IVillagerNPC;
import org.bukkit.entity.Player;

import java.util.UUID;

public enum InteractionTargetType {
    ADULT,
    CHILD,
    CHILD_OFFSPRING,
    PARTNER;

    public String getName() {
        return name().toLowerCase().replace("_", "-");
    }

    public static InteractionTargetType getInteractionTarget(IVillagerNPC npc, Player player) {
        UUID playerUUID = player.getUniqueId();
        if (npc.isPartner(playerUUID)) return PARTNER;
        else if (npc.getFather() != null && playerUUID.equals(npc.getFather().getUniqueId())) return CHILD_OFFSPRING;
        else if (npc.bukkit().isAdult()) return ADULT;
        return CHILD;
    }
}