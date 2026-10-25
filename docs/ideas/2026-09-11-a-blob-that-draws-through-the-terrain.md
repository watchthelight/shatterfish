# A blob that draws through the terrain, not through an emitter

Story 1.11's Observer carries a blob only where its own `BlobEmitter` draws particles, which is the
game's own drawing rule for a gas. Two of the Cleric's spells lay blobs that draw nothing that way
and change the floor instead: `WallOfLight.LightWall` sets its cells solid and impassable
(`core/.../actors/hero/spells/WallOfLight.java:244`, `:290-300`) and `HallowedGround.HallowedTerrain`
dresses its cells (`core/.../actors/hero/spells/HallowedGround.java:166`). The player sees both; the
Observation shows neither, and the map's tiles still say floor, so a bot would walk into a wall of
its own light. Nothing leaks, and nothing is wrong before a Cleric plays, but the map wants a row
for what a blob does to a cell's terrain, which is a schema change and a story of its own.
