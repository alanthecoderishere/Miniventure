package com.miniv.core;

import java.util.ArrayList;
import java.util.List;

/**
 * NPC uses the same {@link Player} body / facing as the local avatar for rendering.
 */
public class NPCEntity extends Player {
    public final String id;
    public String name;
    public String[] dialogLines;
    public final List<Scenario> scenarios = new ArrayList<>();
    public float interactRadius = 4.0f;
    /** After first ENTER save, dialog is published and editing is blocked until removed. */
    public boolean locked = false;

    /** One dialog button: label + action run when player picks it. */
    public static class Scenario {
        public String buttonText;
        public final Runnable action;

        public Scenario(String buttonText, Runnable action) {
            this.buttonText = buttonText;
            this.action = action;
        }
    }

    public NPCEntity(String id, String name, float x, float y, float z, String[] dialogLines) {
        this.id = id;
        this.name = name;
        this.dialogLines = dialogLines;
        this.position.set(x, y, z);
        this.facing = 0;
    }

    public void addScenario(String buttonText, Runnable action) {
        scenarios.add(new Scenario(buttonText, action));
    }

    public float distanceTo(float px, float py, float pz) {
        float dx = position.x + 0.5f - px;
        float dy = position.y + 0.5f - py;
        float dz = position.z + 0.5f - pz;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public boolean isInRange(float px, float py, float pz) {
        return distanceTo(px, py, pz) <= interactRadius;
    }
}
