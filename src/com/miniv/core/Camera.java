package com.miniv.core;

import com.miniv.core.Config;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class Camera {
    private Vector3f position;
    private Vector3f target;
    private Matrix4f projectionMatrix;
    private Matrix4f viewMatrix;

    private float orthoSize = Config.zoom;

    public Camera() {
        position = new Vector3f(10, 10, 10);
        target = new Vector3f(0, 0, 0);
        projectionMatrix = new Matrix4f();
        viewMatrix = new Matrix4f();
        updateProjectionMatrix(800, 600); // Default size
    }

    public void updateProjectionMatrix(int width, int height) {
        float aspect = (float) width / (height == 0 ? 1 : (float) height);
        projectionMatrix.identity().ortho(-orthoSize * aspect, orthoSize * aspect, -orthoSize, orthoSize, -200.0f, 200.0f);
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
        // Isometric offset
        position.set(playerPos).add(30, 30, 30);
        target.set(playerPos);
        viewMatrix.identity().lookAt(position, target, new Vector3f(0, 1, 0));
    }

    public Matrix4f getProjectionMatrix() {
        return projectionMatrix;
    }

    public Matrix4f getViewMatrix() {
        return viewMatrix;
    }
}