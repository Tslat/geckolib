package software.bernie.geckolib3.util;

import com.mojang.blaze3d.Blaze3D;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import com.mojang.math.Quaternion;
import com.mojang.math.Vector3f;
import it.unimi.dsi.fastutil.ints.IntIntImmutablePair;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib3.GeckoLib;
import software.bernie.geckolib3.core.animatable.model.GeoBone;
import software.bernie.geckolib3.cache.object.GeoCube;
import software.bernie.geckolib3.model.GeoModel;
import software.bernie.geckolib3.renderers.geo.GeoRenderer;

import javax.annotation.Nullable;

/**
 * Helper class for various methods and functions useful while rendering
 */
public final class RenderUtils {
	public static void translateMatrixToBone(PoseStack poseStack, GeoBone bone) {
		poseStack.translate(-bone.getPosX() / 16f, bone.getPosY() / 16f, bone.getPosZ() / 16f);
	}

	public static void rotateMatrixAroundBone(PoseStack poseStack, GeoBone bone) {
		if (bone.getRotZ() != 0)
			poseStack.mulPose(Vector3f.ZP.rotation(bone.getRotZ()));

		if (bone.getRotY() != 0)
			poseStack.mulPose(Vector3f.YP.rotation(bone.getRotY()));

		if (bone.getRotX() != 0)
			poseStack.mulPose(Vector3f.XP.rotation(bone.getRotX()));
	}

	public static void rotateMatrixAroundCube(PoseStack poseStack, GeoCube cube) {
		Vec3 rotation = cube.rotation();

		poseStack.mulPose(new Quaternion(0, 0, (float)rotation.z(), false));
		poseStack.mulPose(new Quaternion(0, (float)rotation.y(), 0, false));
		poseStack.mulPose(new Quaternion((float)rotation.x(), 0, 0, false));
	}

	public static void scaleMatrixForBone(PoseStack poseStack, GeoBone bone) {
		poseStack.scale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
	}

	public static void translateToPivotPoint(PoseStack poseStack, GeoCube cube) {
		Vec3 pivot = cube.pivot();
		poseStack.translate(pivot.x() / 16f, pivot.y() / 16f, pivot.z() / 16f);
	}

	public static void translateToPivotPoint(PoseStack poseStack, GeoBone bone) {
		poseStack.translate(bone.getPivotX() / 16f, bone.getPivotY() / 16f, bone.getPivotZ() / 16f);
	}

	public static void translateAwayFromPivotPoint(PoseStack poseStack, GeoCube cube) {
		Vec3 pivot = cube.pivot();

		poseStack.translate(-pivot.x() / 16f, -pivot.y() / 16f, -pivot.z() / 16f);
	}

	public static void translateAwayFromPivotPoint(PoseStack poseStack, GeoBone bone) {
		poseStack.translate(-bone.getPivotX() / 16f, -bone.getPivotY() / 16f, -bone.getPivotZ() / 16f);
	}

	public static void translateAndRotateMatrixForBone(PoseStack poseStack, GeoBone bone) {
		translateToPivotPoint(poseStack, bone);
		rotateMatrixAroundBone(poseStack, bone);
	}

	public static void prepMatrixForBone(PoseStack poseStack, GeoBone bone) {
		translateMatrixToBone(poseStack, bone);
		translateToPivotPoint(poseStack, bone);
		rotateMatrixAroundBone(poseStack, bone);
		scaleMatrixForBone(poseStack, bone);
		translateAwayFromPivotPoint(poseStack, bone);
	}

	/**
	 * Gets the actual dimensions of a texture resource from a given path.<br>
	 * Not performance-efficient, and should not be relied upon
	 * @param texture The path of the texture resource to check
	 * @return The dimensions (width x height) of the texture, or null if unable to find or read the file
	 */
	@Nullable
	public static IntIntPair getTextureDimensions(ResourceLocation texture) {
		if (texture == null)
			return null;

		AbstractTexture originalTexture = null;
		Minecraft mc = Minecraft.getInstance();

		try {
			originalTexture = mc.submit(() -> mc.getTextureManager().getTexture(texture)).get();
		}
		catch (Exception e) {
			GeckoLib.LOGGER.warn("Failed to load image for id {}", texture);
			e.printStackTrace();
		}

		if (originalTexture == null)
			return null;

		NativeImage image = null;

		try {
			image = originalTexture instanceof DynamicTexture dynamicTexture ? dynamicTexture.getPixels()
					: NativeImage.read(mc.getResourceManager().getResource(texture).get().open());
		}
		catch (Exception e) {
			GeckoLib.LOGGER.error("Failed to read image for id {}", texture);
			e.printStackTrace();
		}

		return image == null ? null : IntIntImmutablePair.of(image.getWidth(), image.getHeight());
	}

	public static Matrix4f invertAndMultiplyMatrices(Matrix4f baseMatrix, Matrix4f inputMatrix) {
		inputMatrix = inputMatrix.copy();

		inputMatrix.invert();
		inputMatrix.multiply(baseMatrix);

		return inputMatrix;
	}

	public static double getCurrentSystemTick() {
		return System.nanoTime() / 1E6 / 50d;
	}

	public static double getCurrentTick() {
		return Blaze3D.getTime() * 20d;
	}

	public static float booleanToFloat(boolean input) {
		return input ? 1f : 0f;
	}

	public static Vec3 arrayToVec(double[] array) {
		return new Vec3(array[0], array[1], array[2]);
	}

	/**
	 * Rotates a {@link GeoBone} to match a provided {@link ModelPart}'s rotations.<br>
	 * Usually used for items or armor rendering to match the rotations of other non-geo model parts.
	 */
	public static void matchModelPartRot(ModelPart from, GeoBone to) {
		to.setRotX(-from.xRot);
		to.setRotY(-from.yRot);
		to.setRotZ(from.zRot);
	}

	@Nullable
	public static GeoModel<?> getGeoModelForEntity(Entity entity) {
		EntityRenderer<?> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);

		return renderer instanceof GeoRenderer<?> geoRenderer ? geoRenderer.geoGeoModel() : null;
	}

	@Nullable
	public static GeoModel<?> getGeoModelForItem(Item item) {
		if (IClientItemExtensions.of(item).getCustomRenderer() instanceof GeoRenderer<?> geoRenderer)
			return geoRenderer.geoGeoModel();

		return null;
	}

	@Nullable
	public static GeoModel<?> getGeoModelForBlock(BlockEntity blockEntity) {
		BlockEntityRenderer<?> renderer = Minecraft.getInstance().getBlockEntityRenderDispatcher().getRenderer(blockEntity);

		return renderer instanceof GeoRenderer<?> geoRenderer ? geoRenderer.geoGeoModel() : null;
	}
}
