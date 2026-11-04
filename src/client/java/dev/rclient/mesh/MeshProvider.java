package dev.rclient.mesh;

/** Something that contributes quads (in player model space) to the cosmetic mesh. */
public interface MeshProvider {
    void build(MeshBuilder b, CosmeticContext c);
}
