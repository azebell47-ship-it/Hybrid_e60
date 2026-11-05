
private void initializeLights() {

    // Soleil : éclairage général du toit.
    sunEntity = EntityManager.get().create();

    new LightManager.Builder(LightManager.Type.SUN)
            .color(1.0f, 0.98f, 0.96f)
            .intensity(100000f)
            .direction(-0.4f, -1.0f, -0.3f)
            .build(engine, sunEntity);

    scene.addEntity(sunEntity);

    // Éclairage principal du côté visible.
    fillEntity = EntityManager.get().create();

    new LightManager.Builder(
            LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(100000f)
            .direction(0.8f, -0.15f, -0.6f)
            .build(engine, fillEntity);

    scene.addEntity(fillEntity);

    // Éclairage opposé.
    frontEntity = EntityManager.get().create();

    new LightManager.Builder(
            LightManager.Type.DIRECTIONAL)
            .color(0.92f, 0.96f, 1.0f)
            .intensity(65000f)
            .direction(-0.8f, -0.2f, 0.5f)
            .build(engine, frontEntity);

    scene.addEntity(frontEntity);

    // Éclairage doux des surfaces verticales.
    rearEntity = EntityManager.get().create();

    new LightManager.Builder(
            LightManager.Type.DIRECTIONAL)
            .color(1.0f, 1.0f, 1.0f)
            .intensity(45000f)
            .direction(0.0f, -0.1f, 1.0f)
            .build(engine, rearEntity);

    scene.addEntity(rearEntity);
}
