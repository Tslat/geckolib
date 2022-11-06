package software.bernie.geckolib3.core.animation;

import com.eliotlash.mclib.utils.Interpolations;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import software.bernie.geckolib3.core.animatable.GeoAnimatable;
import software.bernie.geckolib3.core.animatable.model.BakedGeoModel;
import software.bernie.geckolib3.core.animatable.model.GeoBone;
import software.bernie.geckolib3.core.animatable.model.GeoModel;
import software.bernie.geckolib3.core.keyframe.AnimationPoint;
import software.bernie.geckolib3.core.keyframe.BoneAnimationQueue;
import software.bernie.geckolib3.core.state.BoneSnapshot;

import java.util.*;

public class AnimationProcessor<T extends GeoAnimatable> {
	private final Map<String, GeoBone> bones = new Object2ObjectOpenHashMap<>();
	private final GeoModel<T> model;

	public boolean reloadAnimations = false;

	public AnimationProcessor(GeoModel<T> model) {
		this.model = model;
	}

	/**
	 * Build an animation queue for the given {@link RawAnimation}
	 * @param animatable The animatable object being rendered
	 * @param rawAnimation The raw animation to be compiled
	 * @return A queue of animations and loop types to play
	 */
	public Queue<QueuedAnimation> buildAnimationQueue(T animatable, RawAnimation rawAnimation) {
		LinkedList<QueuedAnimation> animations = new LinkedList<>();
		boolean error = false;

		for (RawAnimation.Stage stage : rawAnimation.getAnimationStages()) {
			Animation animation = model.getAnimation(animatable, stage.animationName());

			if (animation == null) {
				System.out.printf("Could not load animation: %s. Is it missing?", stage.animationName());

				error = true;
			}
			else {
				animations.add(new QueuedAnimation(animation, stage.loopType()));
			}
		}

		return error ? null : animations;
	}

	/**
	 * Tick and apply transformations to the model based on the current state of the {@link AnimationController}
	 * @param animatable The animatable object relevant to the animation being played
	 * @param instanceId The {@code int} id for the instance being rendered
	 * @param seekTime The current lerped tick (current tick + partial tick)
	 * @param event An {@link AnimationEvent} instance applied to this render frame
	 * @param crashWhenCantFindBone Whether to crash if unable to find a required bone, or to continue with the remaining bones
	 */
	public void tickAnimation(T animatable, int instanceId, double seekTime, AnimationEvent<T> event, boolean crashWhenCantFindBone) {
		AnimationData<T> animationData = animatable.getFactory().getOrCreateAnimationData(instanceId);
		Map<String, BoneSnapshot> boneSnapshots = updateBoneSnapshots(animationData.getBoneSnapshotCollection());
		List<GeoBone> modifiedBones = new ObjectArrayList<>();

		resetBoneTransformationMarkers();

		for (AnimationController<T> controller : animationData.getAnimationControllers().values()) {
			if (this.reloadAnimations) {
				controller.markNeedsReload();
				controller.getBoneAnimationQueues().clear();
			}

			controller.isJustStarting = animationData.isFirstTick();

			event.withController(controller);
			controller.process(seekTime, event, this.bones.values(), boneSnapshots, crashWhenCantFindBone);

			for (BoneAnimationQueue boneAnimation : controller.getBoneAnimationQueues().values()) {
				GeoBone bone = boneAnimation.bone();
				BoneSnapshot snapshot = boneSnapshots.get(bone.getName());
				BoneSnapshot initialSnapshot = bone.getInitialSnapshot();

				AnimationPoint rotXPoint = boneAnimation.rotationXQueue().poll();
				AnimationPoint rotYPoint = boneAnimation.rotationYQueue().poll();
				AnimationPoint rotZPoint = boneAnimation.rotationZQueue().poll();
				AnimationPoint posXPoint = boneAnimation.positionXQueue().poll();
				AnimationPoint posYPoint = boneAnimation.positionYQueue().poll();
				AnimationPoint posZPoint = boneAnimation.positionZQueue().poll();
				AnimationPoint scaleXPoint = boneAnimation.scaleXQueue().poll();
				AnimationPoint scaleYPoint = boneAnimation.scaleYQueue().poll();
				AnimationPoint scaleZPoint = boneAnimation.scaleZQueue().poll();
				EasingType easingType = controller.overrideEasingTypeFunction.apply(animatable);

				if (rotXPoint != null && rotYPoint != null && rotZPoint != null) {
					bone.setRotX((float)EasingType.lerpWithOverride(rotXPoint, easingType) + initialSnapshot.getRotX());
					bone.setRotY((float)EasingType.lerpWithOverride(rotYPoint, easingType) + initialSnapshot.getRotY());
					bone.setRotZ((float)EasingType.lerpWithOverride(rotZPoint, easingType) + initialSnapshot.getRotZ());

					snapshot.updateRotation(bone.getRotX(), bone.getRotY(), bone.getRotZ());
					snapshot.startRotAnim();

					bone.markRotationAsChanged();
					modifiedBones.add(bone);
				}

				if (posXPoint != null && posYPoint != null && posZPoint != null) {
					bone.setPosX(
							(float)EasingType.lerpWithOverride(posXPoint, easingType));
					bone.setPosY(
							(float)EasingType.lerpWithOverride(posYPoint, easingType));
					bone.setPosZ(
							(float)EasingType.lerpWithOverride(posZPoint, easingType));
					snapshot.updateOffset(bone.getPosX(), bone.getPosY(), bone.getPosZ());
					snapshot.startPosAnim();

					bone.markPositionAsChanged();
					modifiedBones.add(bone);
				}

				if (scaleXPoint != null && scaleYPoint != null && scaleZPoint != null) {
					bone.setScaleX((float)EasingType.lerpWithOverride(scaleXPoint, easingType));
					bone.setScaleY((float)EasingType.lerpWithOverride(scaleYPoint, easingType));
					bone.setScaleZ((float)EasingType.lerpWithOverride(scaleZPoint, easingType));
					snapshot.updateScale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
					snapshot.startScaleAnim();

					bone.markScaleAsChanged();
					modifiedBones.add(bone);
				}
			}
		}

		this.reloadAnimations = false;
		double resetTickLength = animatable.getBoneResetTime();

		for (GeoBone bone : modifiedBones) {
			BoneSnapshot initialSnapshot = bone.getInitialSnapshot();
			BoneSnapshot saveSnapshot = boneSnapshots.get(bone.getName());

			if (saveSnapshot == null) {
				if (crashWhenCantFindBone) {
					throw new RuntimeException(
							"Could not find save snapshot for bone: " + bone.getName() + ". Please don't add bones that are used in an animation at runtime.");
				}
				else {
					continue;
				}
			}

			if (!bone.hasRotationChanged()) {
				if (saveSnapshot.isRotAnimInProgress())
					saveSnapshot.stopRotAnim(seekTime);

				double percentageReset = Math
						.min((seekTime - saveSnapshot.getLastResetRotationTick()) / resetTickLength, 1);

				bone.setRotX((float)Interpolations.lerp(saveSnapshot.getRotX(),
						initialSnapshot.getRotX(), percentageReset));
				bone.setRotY((float)Interpolations.lerp(saveSnapshot.getRotY(),
						initialSnapshot.getRotY(), percentageReset));
				bone.setRotZ((float)Interpolations.lerp(saveSnapshot.getRotZ(),
						initialSnapshot.getRotZ(), percentageReset));

				if (percentageReset >= 1)
					saveSnapshot.updateRotation(bone.getRotX(), bone.getRotY(), bone.getRotZ());
			}

			if (!bone.hasPositionChanged()) {
				if (saveSnapshot.isPosAnimInProgress())
					saveSnapshot.stopPosAnim(seekTime);

				double percentageReset = Math
						.min((seekTime - saveSnapshot.getLastResetPositionTick()) / resetTickLength, 1);

				bone.setPosX((float)Interpolations.lerp(saveSnapshot.getOffsetX(),
						initialSnapshot.getOffsetX(), percentageReset));
				bone.setPosY((float)Interpolations.lerp(saveSnapshot.getOffsetY(),
						initialSnapshot.getOffsetY(), percentageReset));
				bone.setPosZ((float)Interpolations.lerp(saveSnapshot.getOffsetZ(),
						initialSnapshot.getOffsetZ(), percentageReset));

				if (percentageReset >= 1)
					saveSnapshot.updateOffset(bone.getPosX(), bone.getPosY(), bone.getPosZ());
			}

			if (!bone.hasScaleChanged()) {
				if (saveSnapshot.isScaleAnimInProgress())
					saveSnapshot.stopScaleAnim(seekTime);

				double percentageReset = Math.min((seekTime - saveSnapshot.getLastResetScaleTick()) / resetTickLength, 1);

				bone.setScaleX((float)Interpolations.lerp(saveSnapshot.getScaleX(), initialSnapshot.getScaleX(), percentageReset));
				bone.setScaleY((float)Interpolations.lerp(saveSnapshot.getScaleY(), initialSnapshot.getScaleY(), percentageReset));
				bone.setScaleZ((float)Interpolations.lerp(saveSnapshot.getScaleZ(), initialSnapshot.getScaleZ(), percentageReset));

				if (percentageReset >= 1)
					saveSnapshot.updateScale(bone.getScaleX(), bone.getScaleY(), bone.getScaleZ());
			}
		}

		animationData.finishFirstTick();
	}

	/**
	 * Reset the transformation markers applied to each {@link GeoBone} ready for the next render frame
	 */
	private void resetBoneTransformationMarkers() {
		getRegisteredBones().forEach(GeoBone::resetStateChanges);
	}

	/**
	 * Create new bone {@link BoneSnapshot} based on the bone's initial snapshot for the currently registered {@link GeoBone GeoBones},
	 * filtered by the bones already present in the master snapshots map
	 * @param snapshots The master bone snapshots map from the related {@link AnimationData}
	 * @return The input snapshots map, for easy assignment
	 */
	private Map<String, BoneSnapshot> updateBoneSnapshots(Map<String, BoneSnapshot> snapshots) {
		for (GeoBone bone : getRegisteredBones()) {
			if (!snapshots.containsKey(bone.getName()))
				snapshots.put(bone.getName(), BoneSnapshot.copy(bone.getInitialSnapshot()));
		}

		return snapshots;
	}

	/**
	 * Gets a bone by name.
	 *
	 * @param boneName The bone name
	 * @return the bone
	 */
	public GeoBone getBone(String boneName) {
		return this.bones.get(boneName);
	}

	/**
	 * Adds the given bone to the bones list for this processor.<br>
	 * This is normally handled automatically by Geckolib.<br>
	 * Failure to properly register a bone will break things.
	 */
	public void registerGeoBone(GeoBone bone) {
		bone.saveInitialSnapshot();
		this.bones.put(bone.getName(), bone);

		for (GeoBone child : bone.getChildBones()) {
			registerGeoBone(bone);
		}
	}

	/**
	 * Clear the {@link GeoBone GeoBones} currently registered to the processor,
	 * then prepares the processor for a new model.<br>
	 * Should be called whenever switching models to render/animate
	 */
	public void setActiveModel(BakedGeoModel model) {
		this.bones.clear();

		for (GeoBone bone : model.getBones()) {
			registerGeoBone(bone);
		}
	}

	/**
	 * Get an iterable collection of the {@link GeoBone GeoBones} currently registered to the processor
	 */
	public Collection<GeoBone> getRegisteredBones() {
		return this.bones.values();
	}

	/**
	 * Apply transformations and settings prior to acting on any animation-related functionality
	 */
	public void preAnimationSetup(T animatable, double seekTime) {
		this.model.applyMolangQueries(animatable, seekTime);
	}

	/**
	 * {@link Animation} and {@link software.bernie.geckolib3.core.animation.Animation.LoopType} override pair,
	 * used to define a playable animation stage for a {@link software.bernie.geckolib3.core.animatable.GeoAnimatable}
	 */
	public record QueuedAnimation(Animation animation, Animation.LoopType loopType) {}
}
