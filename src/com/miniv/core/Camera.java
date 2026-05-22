package com.miniv.core;

import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private Vector3f target;
    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;

    private float orthoSize = Config.zoom;

    // Camera rotation: yaw = Y-axis spin, pitch = X-axis tilt, roll = Z-axis roll
    // All in degrees. Isometric default = yaw 45, pitch ~35.26 (arctan(1/√2)).
    private float yaw   =  45f;
    private float pitch =  35.26f;
    private float roll  =   0f;

    // Which axis is currently being rotated by V key
    // 0 = Y (yaw), 1 = X (pitch), 2 = Z (roll)
    private int rotateAxis = 0;
    private static final float ROTATE_STEP = 15f; // degrees per V press

    public Camera() {
        position = new Vector3f(10, 10, 10);
        target   = new Vector3f(0, 0, 0);
        projectionMatrix = new Matrix4f();
        viewMatrix       = new Matrix4f();
        updateProjectionMatrix(800, 600);
    }

    /**
     * Cycle the active rotation axis: Y → X → Z → Y …
     * Returns a description string for the chat message.
     */
    public String cycleAxis() {
        rotateAxis = (rotateAxis + 1) % 3;
        return new String[]{"Y (Yaw)", "X (Pitch)", "Z (Roll)"}[rotateAxis];
    }

    /**
     * Rotate the camera by ROTATE_STEP degrees on the currently-active axis.
     * Returns a description string for the chat message.
     */
    public String rotate() {
        switch (rotateAxis) {
            case 0: yaw   = (yaw   + ROTATE_STEP) % 360f; return String.format("Yaw %.0f°",   yaw);
            case 1: pitch = (pitch + ROTATE_STEP) % 360f; return String.format("Pitch %.0f°", pitch);
            case 2: roll  = (roll  + ROTATE_STEP) % 360f; return String.format("Roll %.0f°",  roll);
        }
        return "";
    }

    public void updateProjectionMatrix(int width, int height) {
        float aspect = (float) width / (height == 0 ? 1 : (float) height);
        projectionMatrix.identity().ortho(
            -orthoSize * aspect, orthoSize * aspect,
            -orthoSize,  orthoSize,
            -200.0f, 200.0f);
    }

    public void zoom(float offset, int width, int height) {
        orthoSize -= offset;
        if (orthoSize < 1.0f) orthoSize = 1.0f;
        float max = Config.getMaxZoom();
        if (orthoSize > max) orthoSize = max;
        Config.zoom = orthoSize;
        updateProjectionMatrix(width, height);
    }

    public void setZoom(float value, int width, int height) {
        orthoSize = value;
        if (orthoSize < 1.0f) orthoSize = 1.0f;
        float max = Config.getMaxZoom();
        if (orthoSize > max) orthoSize = max;
        Config.zoom = orthoSize;
        updateProjectionMatrix(width, height);
    }

    public void follow(Vector3f playerPos) {
        // Build a rotation matrix from yaw, pitch, roll
        // Then extract the camera position by rotating the canonical isometric
        // offset (30, 30, 30) around the player.
        float dist = 30f;

        double yR = Math.toRadians(yaw);
        double pR = Math.toRadians(pitch);
        double rR = Math.toRadians(roll);

        // Direction from target to camera using spherical coordinates:
        //   x = cos(pitch)*sin(yaw)
        //   y = sin(pitch)
        //   z = cos(pitch)*cos(yaw)
        float cosP = (float) Math.cos(pR);
        float ex   = cosP * (float) Math.sin(yR);
        float ey   = (float) Math.sin(pR);
        float ez   = cosP * (float) Math.cos(yR);

        position.set(
            playerPos.x + ex * dist,
            playerPos.y + ey * dist,
            playerPos.z + ez * dist
        );
        target.set(playerPos);

        // "Up" vector rotated by roll
        float cosR = (float) Math.cos(rR);
        float sinR = (float) Math.sin(rR);

        // Right vector perpendicular to (ex,ey,ez) and world-up (0,1,0),
        // then up = right × forward rotated by roll
        Vector3f fwd   = new Vector3f(-ex, -ey, -ez); // camera looks toward player
        Vector3f worldUp = new Vector3f(0, 1, 0);
        Vector3f right = new Vector3f(fwd).cross(worldUp).normalize();
        // up = worldUp rotated by roll in the right/worldUp plane
        Vector3f up = new Vector3f(
            worldUp.x * cosR + right.x * sinR,
            worldUp.y * cosR + right.y * sinR,
            worldUp.z * cosR + right.z * sinR
        );

        viewMatrix.identity().lookAt(position, target, up);
    }

    public Matrix4f getProjectionMatrix() { return projectionMatrix; }
    public Matrix4f getViewMatrix()       { return viewMatrix; }
}