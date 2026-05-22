package com.miniv.core;

import com.miniv.world.Voxel;
import com.miniv.world.World;
import static org.lwjgl.glfw.GLFW.*;

public class Player extends Entity {
    public int facing = 0; // 0=FRONT, 1=BACK, 2=LEFT, 3=RIGHT
    private float speed = 5.0f;

    private float velocityY = 0.0f;
    private boolean onGround = false;

    public void landAt(float x, float y, float z) {
        position.set(x, y, z);
        velocityY = 0.0f;
        onGround = true;
    }

    public void update(float delta, World world) {
        float dx = 0;
        float dz = 0;

        if (Main.isKeyPressed(GLFW_KEY_W)) {
            dz -= speed * delta;
            facing = 1;
        } 
        else if (Main.isKeyPressed(GLFW_KEY_S)) {
            dz += speed * delta;
            facing = 0;
        }
        else if (Main.isKeyPressed(GLFW_KEY_A)) {
            dx -= speed * delta;
            facing = 2;
        } 
        else if (Main.isKeyPressed(GLFW_KEY_D)) {
            dx += speed * delta;
            facing = 3;
        }

        if (Main.isKeyPressed(GLFW_KEY_SPACE) && onGround) {
            velocityY = 6.5f;
            onGround = false;
        }

        velocityY -= 15.0f * delta; // Gravity

        // Move X
        if (!collides(position.x + dx, position.y, position.z, world)) {
            position.x += dx;
        }
        // Move Z
        if (!collides(position.x, position.y, position.z + dz, world)) {
            position.z += dz;
        }
        // Move Y
        float nextY = position.y + velocityY * delta;
        if (!collides(position.x, nextY, position.z, world)) {
            position.y = nextY;
            onGround = false;
        } else {
            if (velocityY < 0) {
                onGround = true;
                position.y = (float) Math.floor(position.y); // Snap to ground
            }
            velocityY = 0;
        }
    }

    private boolean collides(float px, float py, float pz, World world) {
        float width = 0.98f; // Match the 1.0x1.0 visual size closely
        float height = 0.98f;

        return isSolid(px + 0.5f - width/2, py, pz + 0.5f - width/2, world) ||
               isSolid(px + 0.5f + width/2, py, pz + 0.5f - width/2, world) ||
               isSolid(px + 0.5f - width/2, py, pz + 0.5f + width/2, world) ||
               isSolid(px + 0.5f + width/2, py, pz + 0.5f + width/2, world) ||
               isSolid(px + 0.5f - width/2, py + height, pz + 0.5f - width/2, world) ||
               isSolid(px + 0.5f + width/2, py + height, pz + 0.5f - width/2, world) ||
               isSolid(px + 0.5f - width/2, py + height, pz + 0.5f + width/2, world) ||
               isSolid(px + 0.5f + width/2, py + height, pz + 0.5f + width/2, world);
    }

    private boolean isSolid(float x, float y, float z, World world) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (by >= com.miniv.core.Config.worldHeight || by < 0) return false;
        return world.getBlock(bx, by, bz) != Voxel.AIR;
    }

}
