/*
 * Copyright (c) 2020.
 * Author: Bernie G. (Gecko)
 */

package software.bernie.geckolib3.core.animation;

import com.eliotlash.mclib.math.Constant;
import com.eliotlash.mclib.math.IValue;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import software.bernie.geckolib3.core.PlayState;
import software.bernie.geckolib3.core.animatable.GeoAnimatable;
import software.bernie.geckolib3.core.animatable.model.GeoBone;
import software.bernie.geckolib3.core.animatable.model.GeoModel;
import software.bernie.geckolib3.core.keyframe.event.CustomInstructionKeyframeEvent;
import software.bernie.geckolib3.core.keyframe.event.data.CustomInstructionKeyframeData;
import software.bernie.geckolib3.core.keyframe.event.data.KeyFrameData;
import software.bernie.geckolib3.core.keyframe.event.data.ParticleKeyframeData;
import software.bernie.geckolib3.core.keyframe.event.ParticleKeyframeEvent;
import software.bernie.geckolib3.core.keyframe.event.SoundKeyframeEvent;
import software.bernie.geckolib3.core.keyframe.*;
import software.bernie.geckolib3.core.keyframe.event.data.SoundKeyframeData;
import software.bernie.geckolib3.core.molang.MolangParser;
import software.bernie.geckolib3.core.molang.MolangQueries;
import software.bernie.geckolib3.core.state.BoneSnapshot;
import software.bernie.geckolib3.core.util.Axis;

import java.util.*;
import java.util.function.Function;

/**
 * The actual controller that handles the playing and usage of animations, including their various keyframes and instruction markers.
 * Each controller can only play a single animation at a time - for example you may have one controller to animate walking,
 * one to control attacks, one to control size, etc.
 */
@SuppressWarnings({"UnusedReturnValue", "unused"})
public class AnimationController<T extends GeoAnimatable> {
	protected final T animatable;
	protected final String name;
	protected final AnimationStateHandler<T> stateHandler;
	protected final double transitionLength;
	protected final Map<String, BoneAnimationQueue> boneAnimationQueues = new Object2ObjectOpenHashMap<>();
	protected final Map<String, BoneSnapshot> boneSnapshots = new Object2ObjectOpenHashMap<>();
	protected Queue<AnimationProcessor.QueuedAnimation> animationQueue = new LinkedList<>();

	protected boolean isJustStarting = false;
	protected boolean needsAnimationReload = false;
	protected boolean shouldResetTick = false;
	private boolean justStopped = false;
	protected boolean justStartedTransition = false;

	protected SoundKeyframeHandler<T> soundKeyframeHandler;
	protected ParticleKeyframeHandler<T> particleKeyframeHandler;
	protected CustomKeyframeHandler<T> customKeyframeHandler;

	protected RawAnimation currentRawAnimation;
	protected AnimationProcessor.QueuedAnimation currentAnimation;
	protected State animationState = State.STOPPED;
	protected double tickOffset;
	protected Function<T, Double> animationSpeedModifier = animatable -> 1d;
	protected Function<T, EasingType> easingTypeFunction = animatable -> EasingType.LINEAR;
	private final Set<KeyFrameData> executedKeyFrames = new ObjectOpenHashSet<>();

	/**
	 * Instantiates a new {@code AnimationController}.<br>
	 * This constructor assumes a 0-tick transition length between animations, and a generic name.
	 * @param animatable The object that will be animated by this controller
	 * @param animationHandler The {@link AnimationStateHandler} animation state handler responsible for deciding which animations to play
	 */
	public AnimationController(T animatable, AnimationStateHandler<T> animationHandler) {
		this(animatable, "base_controller", 0, animationHandler);
	}

	/**
	 * Instantiates a new {@code AnimationController}.<br>
	 * This constructor assumes a 0-tick transition length between animations.
	 * @param animatable The object that will be animated by this controller
	 * @param name The name of the controller - should represent what animations it handles
	 * @param animationHandler The {@link AnimationStateHandler} animation state handler responsible for deciding which animations to play
	 */
	public AnimationController(T animatable, String name, AnimationStateHandler<T> animationHandler) {
		this(animatable, name, 0, animationHandler);
	}

	/**
	 * Instantiates a new {@code AnimationController}.<br>
	 * This constructor assumes a generic name.
	 * @param animatable The object that will be animated by this controller
	 * @param transitionTickTime The amount of time (in <b>ticks</b>) that the controller should take to transition between animations.
	 *                              Lerping is automatically applied where possible
	 * @param animationHandler The {@link AnimationStateHandler} animation state handler responsible for deciding which animations to play
	 */
	public AnimationController(T animatable, int transitionTickTime, AnimationStateHandler<T> animationHandler) {
		this(animatable, "base_controller", transitionTickTime, animationHandler);
	}

	/**
	 * Instantiates a new {@code AnimationController}.<br>
	 * @param animatable The object that will be animated by this controller
	 * @param name The name of the controller - should represent what animations it handles
	 * @param transitionTickTime The amount of time (in <b>ticks</b>) that the controller should take to transition between animations.
	 *                              Lerping is automatically applied where possible
	 * @param animationHandler The {@link AnimationStateHandler} animation state handler responsible for deciding which animations to play
	 */
	public AnimationController(T animatable, String name, int transitionTickTime, AnimationStateHandler<T> animationHandler) {
		this.animatable = animatable;
		this.name = name;
		this.transitionLength = transitionTickTime;
		this.stateHandler = animationHandler;
	}

	/**
	 * Applies the given {@link SoundKeyframeHandler} to this controller, for handling {@link SoundKeyframeEvent sound keyframe instructions}.
	 * @return this
	 */
	public AnimationController<T> setSoundKeyframeHandler(SoundKeyframeHandler<T> soundHandler) {
		this.soundKeyframeHandler = soundHandler;

		return this;
	}

	/**
	 * Applies the given {@link ParticleKeyframeHandler} to this controller, for handling {@link ParticleKeyframeEvent particle keyframe instructions}.
	 * @return this
	 */
	public AnimationController<T> setParticleKeyframeHandler(ParticleKeyframeHandler<T> particleHandler) {
		this.particleKeyframeHandler = particleHandler;

		return this;
	}

	/**
	 * Applies the given {@link CustomKeyframeHandler} to this controller, for handling {@link CustomInstructionKeyframeEvent sound keyframe instructions}.
	 * @return this
	 */
	public AnimationController<T> setCustomInstructionKeyframeHandler(CustomKeyframeHandler<T> customInstructionHandler) {
		this.customKeyframeHandler = customInstructionHandler;

		return this;
	}

	/**
	 * Applies the given modifier function to this controller, for handling the speed that the controller should play its animations at.<br>
	 * An output value of 1 is considered neutral, with 2 playing an animation twice as fast, 0.5 playing half as fast, etc.
	 * @param speedModFunction The function to apply to this controller to handle animation speed
	 * @return this
	 */
	public AnimationController<T> setAnimationSpeedHandler(Function<T, Double> speedModFunction) {
		this.animationSpeedModifier = speedModFunction;

		return this;
	}

	/**
	 * Applies the given modifier value to this controller, for handlign the speed that the controller hsould play its animations at.<br>
	 * A value of 1 is considered neutral, with 2 playing an animation twice as fast, 0.5 playing half as fast, etc.
	 * @param speed The speed modifier to apply to this controller to handle animation speed.
	 * @return this
	 */
	public AnimationController<T> setAnimationSpeed(double speed) {
		return setAnimationSpeedHandler(animatable -> speed);
	}

	/**
	 * Overrides the default ({@link EasingType#LINEAR}) {@code EasingType} for the controller
	 * @param easingTypeFunction The new {@code EasingType} to use
	 * @return this
	 */
	public AnimationController<T> setEasingTypeFunction(EasingType easingTypeFunction) {
		return setEasingType(animatable -> easingTypeFunction);
	}

	/**
	 * Overrides the default ({@link EasingType#LINEAR}) {@code EasingType} function for the controller
	 * @param easingType The new {@code EasingType} to use
	 * @return this
	 */
	public AnimationController<T> setEasingType(Function<T, EasingType> easingType) {
		this.easingTypeFunction = easingType;

		return this;
	}

	/**
	 * Gets the controller's name.
	 * @return The name
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Gets the currently loaded {@link Animation}. Can be null<br>
	 * An animation returned here does not guarantee it is currently playing, just that it is the currently loaded animation for this controller
	 */

	public AnimationProcessor.QueuedAnimation getCurrentAnimation() {
		return this.currentAnimation;
	}

	/**
	 * Returns the current state of this controller.
	 */
	public State getAnimationState() {
		return this.animationState;
	}

	/**
	 * Gets the currently loaded animation's {@link BoneAnimationQueue BoneAnimationQueues}.
	 */
	public Map<String, BoneAnimationQueue> getBoneAnimationQueues() {
		return this.boneAnimationQueues;
	}

	/**
	 * Gets the current animation speed modifier.<br>
	 * This modifier defines the relative speed in which animations will be played based on the current state of the game.
	 * @return The computed current animation speed modifier
	 */
	public double getAnimationSpeed() {
		return this.animationSpeedModifier.apply(this.animatable);
	}

	/**
	 * Marks the controller as needing to reset its animation and state the next time {@link AnimationController#setAnimation(RawAnimation)} is called.
	 */
	public void markNeedsReload() {
		this.needsAnimationReload = true;
	}

	/**
	 * Tells the controller to stop all animations until told otherwise.<br>
	 * Calling this will prevent the controller from continuing to play the currently loaded animation until
	 * either {@link AnimationController#markNeedsReload()} is called, or
	 * {@link AnimationController#setAnimation(RawAnimation)} is called with a different animation
	 */
	public void stop() {
		this.animationState = State.STOPPED;
	}

	/**
	 * Sets the currently loaded animation to the one provided.<br>
	 * This method may be safely called every render frame, as passing the same builder that is already loaded will do nothing.<br>
	 * Pass null to this method to tell the controller to stop.<br>
	 * If {@link AnimationController#markNeedsReload()} has been called prior to this, the controller will reload the animation regardless of whether it matches the currently loaded one or not
	 * @param rawAnimation
	 */
	public void setAnimation(RawAnimation rawAnimation) {
		if (rawAnimation == null || rawAnimation.getAnimationStages().isEmpty()) {
			stop();

			return;
		}

		if (this.needsAnimationReload || !rawAnimation.equals(this.currentRawAnimation)) {
			GeoModel<T> model = this.animatable.getGeoModel().get();

			if (model != null) {
				Queue<AnimationProcessor.QueuedAnimation> animations = model.getAnimationProcessor().buildAnimationQueue(this.animatable, rawAnimation);

				if (animations != null) {
					this.animationQueue = animations;
					this.currentRawAnimation = rawAnimation;
					this.shouldResetTick = true;
					this.animationState = State.TRANSITIONING;
					this.justStartedTransition = true;
					this.needsAnimationReload = false;

					return;
				}
			}

			stop();
		}
	}

	/**
	 * This method is called every frame in order to populate the animation point
	 * queues, and process animation state logic.
	 *
	 * @param seekTime                   The current tick + partial tick
	 * @param event                  The animation test event
	 * @param bones      The registered {@link GeoBone bones} for this model
	 * @param snapshots The {@link BoneSnapshot} map
	 * @param crashWhenCantFindBone Whether to hard-fail when a bone can't be found, or to continue with the remaining bones
	 */
	public void process(final double seekTime, AnimationEvent<T> event, Collection<GeoBone> bones,
						Map<String, BoneSnapshot> snapshots, boolean crashWhenCantFindBone) {
		double adjustedTick = adjustTick(seekTime);

		createInitialQueues(bones);

		if (animationState == State.TRANSITIONING && adjustedTick >= this.transitionLength) {
			this.shouldResetTick = true;
			this.animationState = State.RUNNING;
			adjustedTick = adjustTick(seekTime);
		}

		PlayState playState = this.stateHandler.handle(event);

		if (playState == PlayState.STOP || (this.currentAnimation == null && this.animationQueue.isEmpty())) {
			this.animationState = State.STOPPED;
			this.justStopped = true;

			return;
		}

		if (this.justStartedTransition && (this.shouldResetTick || this.justStopped)) {
			this.justStopped = false;
			adjustedTick = adjustTick(seekTime);
		}
		else if (this.currentAnimation == null) {
			this.shouldResetTick = true;
			this.animationState = State.TRANSITIONING;
			this.justStartedTransition = true;
			this.needsAnimationReload = false;
			adjustedTick = adjustTick(seekTime);
		}
		else if (this.animationState != State.TRANSITIONING) {
			this.animationState = State.RUNNING;
		}

		if (getAnimationState() == State.RUNNING) {
			processCurrentAnimation(adjustedTick, seekTime, crashWhenCantFindBone);
		}
		else if (this.animationState == State.TRANSITIONING) {
			if (adjustedTick == 0 || this.isJustStarting) {
				this.justStartedTransition = false;
				this.currentAnimation = this.animationQueue.poll();

				resetEventKeyFrames();

				if (currentAnimation == null)
					return;

				saveSnapshotsForAnimation(this.currentAnimation, snapshots);
			}

			if (this.currentAnimation != null) {
				MolangParser.INSTANCE.setValue(MolangQueries.ANIM_TIME, () -> 0);

				for (BoneAnimation boneAnimation : this.currentAnimation.animation().boneAnimations()) {
					BoneAnimationQueue boneAnimationQueue = this.boneAnimationQueues.get(boneAnimation.boneName());
					BoneSnapshot boneSnapshot = this.boneSnapshots.get(boneAnimation.boneName());
					Optional<GeoBone> bone = Optional.empty();

					for (GeoBone testBone : bones) {
						if (testBone.getName().equals(boneAnimation.boneName())) {
							bone = Optional.of(testBone);

							break;
						}
					}

					if (bone.isEmpty()) {
						if (crashWhenCantFindBone)
							throw new RuntimeException("Could not find bone: " + boneAnimation.boneName());

						continue;
					}

					KeyframeStack<Keyframe<IValue>> rotationKeyFrames = boneAnimation.rotationKeyFrames();
					KeyframeStack<Keyframe<IValue>> positionKeyFrames = boneAnimation.positionKeyFrames();
					KeyframeStack<Keyframe<IValue>> scaleKeyFrames = boneAnimation.scaleKeyFrames();

					if (!rotationKeyFrames.xKeyframes().isEmpty()) {
						boneAnimationQueue.addNextRotation(null, adjustedTick, this.transitionLength, boneSnapshot, bone.get().getInitialSnapshot(),
								getAnimationPointAtTick(rotationKeyFrames.xKeyframes(), 0, true, Axis.X),
								getAnimationPointAtTick(rotationKeyFrames.yKeyframes(), 0, true, Axis.Y),
								getAnimationPointAtTick(rotationKeyFrames.zKeyframes(), 0, true, Axis.Z));
					}

					if (!positionKeyFrames.xKeyframes().isEmpty()) {
						boneAnimationQueue.addNextPosition(null, adjustedTick, this.transitionLength, boneSnapshot,
								getAnimationPointAtTick(positionKeyFrames.xKeyframes(), 0, false, Axis.X),
								getAnimationPointAtTick(positionKeyFrames.yKeyframes(), 0, false, Axis.Y),
								getAnimationPointAtTick(positionKeyFrames.zKeyframes(), 0, false, Axis.Z));
					}

					if (!scaleKeyFrames.xKeyframes().isEmpty()) {
						boneAnimationQueue.addNextScale(null, adjustedTick, this.transitionLength, boneSnapshot,
								getAnimationPointAtTick(scaleKeyFrames.xKeyframes(), 0, false, Axis.X),
								getAnimationPointAtTick(scaleKeyFrames.yKeyframes(), 0, false, Axis.Y),
								getAnimationPointAtTick(scaleKeyFrames.zKeyframes(), 0, false, Axis.Z));
					}
				}
			}
		}
	}

	/**
	 * Handle the current animation's state modifications and translations
	 * @param adjustedTick The controller-adjusted tick for animation purposes
	 * @param seekTime The lerped tick (current tick + partial tick)
	 * @param crashWhenCantFindBone Whether the controller should throw an exception when unable to find the required bone, or continue with the remaining bones
	 */
	private void processCurrentAnimation(double adjustedTick, double seekTime, boolean crashWhenCantFindBone) {
		if (adjustedTick >= this.currentAnimation.animation().length()) {
			resetEventKeyFrames();

			if (this.currentAnimation.loopType().shouldPlayAgain(this.animatable, this, this.currentAnimation.animation())) {
				this.shouldResetTick = true;

				adjustedTick = adjustTick(seekTime);
			}
			else {
				AnimationProcessor.QueuedAnimation nextAnimation = this.animationQueue.peek();

				if (nextAnimation == null) {
					this.animationState = State.STOPPED;

					return;
				}
				else {
					this.animationState = State.TRANSITIONING;
					this.shouldResetTick = true;
					this.currentAnimation = this.animationQueue.peek();
				}
			}
		}

		MolangParser.INSTANCE.setValue(MolangQueries.ANIM_TIME, () -> 0);

		for (BoneAnimation boneAnimation : this.currentAnimation.animation().boneAnimations()) {
			BoneAnimationQueue boneAnimationQueue = boneAnimationQueues.get(boneAnimation.boneName());

			if (boneAnimationQueue == null) {
				if (crashWhenCantFindBone)
					throw new RuntimeException("Could not find bone: " + boneAnimation.boneName());

				continue;
			}

			KeyframeStack<Keyframe<IValue>> rotationKeyFrames = boneAnimation.rotationKeyFrames();
			KeyframeStack<Keyframe<IValue>> positionKeyFrames = boneAnimation.positionKeyFrames();
			KeyframeStack<Keyframe<IValue>> scaleKeyFrames = boneAnimation.scaleKeyFrames();

			if (!rotationKeyFrames.xKeyframes().isEmpty()) {
				boneAnimationQueue.addRotations(
						getAnimationPointAtTick(rotationKeyFrames.xKeyframes(), adjustedTick, true, Axis.X),
						getAnimationPointAtTick(rotationKeyFrames.yKeyframes(), adjustedTick, true, Axis.Y),
						getAnimationPointAtTick(rotationKeyFrames.zKeyframes(), adjustedTick, true, Axis.Z));
			}

			if (!positionKeyFrames.xKeyframes().isEmpty()) {
				boneAnimationQueue.addPositions(
						getAnimationPointAtTick(positionKeyFrames.xKeyframes(), adjustedTick, false, Axis.X),
						getAnimationPointAtTick(positionKeyFrames.yKeyframes(), adjustedTick, false, Axis.Y),
						getAnimationPointAtTick(positionKeyFrames.zKeyframes(), adjustedTick, false, Axis.Z));
			}

			if (!scaleKeyFrames.xKeyframes().isEmpty()) {
				boneAnimationQueue.addScales(
						getAnimationPointAtTick(scaleKeyFrames.xKeyframes(), adjustedTick, false, Axis.X),
						getAnimationPointAtTick(scaleKeyFrames.yKeyframes(), adjustedTick, false, Axis.Y),
						getAnimationPointAtTick(scaleKeyFrames.zKeyframes(), adjustedTick, false, Axis.Z));
			}
		}

		for (SoundKeyframeData keyFrameData : this.currentAnimation.animation().keyFrames().sounds()) {
			if (!this.executedKeyFrames.contains(keyFrameData) && adjustedTick >= keyFrameData.getStartTick()) {
				SoundKeyframeEvent<T> event = new SoundKeyframeEvent<>(this.animatable,
						adjustedTick, this, keyFrameData);

				this.soundKeyframeHandler.handle(event);
				this.executedKeyFrames.add(keyFrameData);
			}
		}

		for (ParticleKeyframeData keyFrameData : this.currentAnimation.animation().keyFrames().particles()) {
			if (!this.executedKeyFrames.contains(keyFrameData) && adjustedTick >= keyFrameData.getStartTick()) {
				ParticleKeyframeEvent<T> event = new ParticleKeyframeEvent<>(this.animatable,
						adjustedTick, this, keyFrameData);

				this.particleKeyframeHandler.handle(event);
				this.executedKeyFrames.add(keyFrameData);
			}
		}

		for (CustomInstructionKeyframeData keyFrameData : currentAnimation.animation().keyFrames().customInstructions()) {
			if (!this.executedKeyFrames.contains(keyFrameData) && adjustedTick >= keyFrameData.getStartTick()) {
				CustomInstructionKeyframeEvent<T> event = new CustomInstructionKeyframeEvent<>(this.animatable,
						adjustedTick, this, keyFrameData);

				this.customKeyframeHandler.handle(event);
				this.executedKeyFrames.add(keyFrameData);
			}
		}

		if (this.transitionLength == 0 && shouldResetTick && this.animationState == State.TRANSITIONING)
			this.currentAnimation = animationQueue.poll();
	}

	// TODO: Look into replacing the BoneAnimationQueue functionality, it is very inefficient
	/**
	 * Prepare the {@link BoneAnimationQueue} map for the current render frame
	 * @param modelRendererList The bone list from the {@link AnimationProcessor}
	 */
	private void createInitialQueues(Collection<GeoBone> modelRendererList) {
		this.boneAnimationQueues.clear();

		for (GeoBone modelRenderer : modelRendererList) {
			this.boneAnimationQueues.put(modelRenderer.getName(), new BoneAnimationQueue(modelRenderer));
		}
	}

	/**
	 * Cache the relevant {@link BoneSnapshot BoneSnapshots} for the current {@link software.bernie.geckolib3.core.animation.AnimationProcessor.QueuedAnimation}
	 * for animation lerping
	 * @param animation The {@code QueuedAnimation} to filter {@code BoneSnapshots} for
	 * @param snapshots The master snapshot collection to pull filter from
	 */
	private void saveSnapshotsForAnimation(AnimationProcessor.QueuedAnimation animation, Map<String, BoneSnapshot> snapshots) {
		for (BoneSnapshot snapshot : snapshots.values()) {
			if (animation.animation().boneAnimations() != null) {
				for (BoneAnimation boneAnimation : animation.animation().boneAnimations()) {
					if (boneAnimation.boneName().equals(snapshot.getBone().getName())) {
						this.boneSnapshots.put(boneAnimation.boneName(), BoneSnapshot.copy(snapshot));

						break;
					}
				}
			}
		}
	}

	/**
	 * Adjust a tick value depending on the controller's current state and speed modifier.<br>
	 * Is used when starting a new animation, transitioning, and a few other key areas
	 * @param tick The currently used tick value
	 * @return 0 if {@link AnimationController#shouldResetTick} is set to false, or a {@link AnimationController#animationSpeedModifier} modified value otherwise
	 */
	protected double adjustTick(double tick) {
		if (!this.shouldResetTick)
			return this.animationSpeedModifier.apply(this.animatable) * Math.max(tick - this.tickOffset, 0);

		if (getAnimationState() != State.STOPPED)
			this.tickOffset = tick;

		this.shouldResetTick = false;

		return 0;
	}

	/**
	 * Convert a {@link KeyframeLocation} to an {@link AnimationPoint}
	 */
	private AnimationPoint getAnimationPointAtTick(List<Keyframe<IValue>> frames, double tick, boolean isRotation,
												   Axis axis) {
		KeyframeLocation<Keyframe<IValue>> location = getCurrentKeyFrameLocation(frames, tick);
		Keyframe<IValue> currentFrame = location.keyframe();
		double startValue = currentFrame.getStartValue().get();
		double endValue = currentFrame.getEndValue().get();

		if (isRotation) {
			if (!(currentFrame.getStartValue() instanceof Constant)) {
				startValue = Math.toRadians(startValue);

				if (axis == Axis.X || axis == Axis.Y)
					startValue *= -1;
			}

			if (!(currentFrame.getEndValue() instanceof Constant)) {
				endValue = Math.toRadians(endValue);

				if (axis == Axis.X || axis == Axis.Y)
					endValue *= -1;
			}
		}

		return new AnimationPoint(currentFrame, location.startTick(), currentFrame.getLength(), startValue, endValue);
	}

	/**
	 * Returns the {@link Keyframe} relevant to the current tick time
	 * @param frames The list of {@code KeyFrames} to filter through
	 * @param ageInTicks The current tick time
	 * @return A new {@code KeyFrameLocation} containing the current {@code KeyFrame} and the tick time used to find it
	 */
	private KeyframeLocation<Keyframe<IValue>> getCurrentKeyFrameLocation(List<Keyframe<IValue>> frames,
																		  double ageInTicks) {
		double totalFrameTime = 0;

		for (Keyframe<IValue> frame : frames) {
			totalFrameTime += frame.getLength();

			if (totalFrameTime > ageInTicks)
				return new KeyframeLocation<>(frame, (ageInTicks - (totalFrameTime - frame.getLength())));
		}

		return new KeyframeLocation<>(frames.get(frames.size() - 1), ageInTicks);
	}

	/**
	 * Clear the {@link KeyFrameData} cache in preparation for the next animation
	 */
	private void resetEventKeyFrames() {
		this.executedKeyFrames.clear();
	}

	/**
	 * Every render frame, the {@code AnimationController} will call this handler for <u>each</u> animatable that is being rendered.
	 * This handler defines which animation should be currently playing, and returning a {@link PlayState} to tell the controller what to do next.<br>
	 * Example Usage:<br>
	 * <pre>AnimationFrameHandler myIdleWalkHandler = event -> {
	 * 	if (event.isMoving()) {
	 * 		event.getController().setAnimation(myWalkAnimation);
	 * 	}
	 * 	else {
	 * 		event.getController().setAnimation(myIdleAnimation);
	 * 	}
	 *
	 * 	return PlayState.CONTINUE;
	 * };</pre>
	 */
	@FunctionalInterface
	public interface AnimationStateHandler<A extends GeoAnimatable> {
		/**
		 * The handling method, called each frame.
		 * Return {@link PlayState#CONTINUE} to tell the controller to continue animating,
		 * or return {@link PlayState#STOP} to tell it to stop playing all animations and wait for the next {@code PlayState.CONTINUE} return.
		 */
		PlayState handle(AnimationEvent<A> event);
	}

	/**
	 * A handler for when a predefined sound keyframe is hit.
	 * When the keyframe is encountered, the {@link SoundKeyframeHandler#handle(SoundKeyframeEvent)} method will be called.
	 * Play the sound(s) of your choice at this time.
	 */
	@FunctionalInterface
	public interface SoundKeyframeHandler<A extends GeoAnimatable> {
		void handle(SoundKeyframeEvent<A> event);
	}

	/**
	 * A handler for when a predefined particle keyframe is hit.
	 * When the keyframe is encountered, the {@link ParticleKeyframeHandler#handle(ParticleKeyframeEvent)} method will be called.
	 * Spawn the particles/effects of your choice at this time.
	 */
	@FunctionalInterface
	public interface ParticleKeyframeHandler<A extends GeoAnimatable> {
		void handle(ParticleKeyframeEvent<A> event);
	}

	/**
	 * A handler for pre-defined custom instruction keyframes.
	 * When the keyframe is encountered, the {@link CustomKeyframeHandler#handle(CustomInstructionKeyframeEvent)} method will be called.
	 * You can then take whatever action you want at this point.
	 */
	@FunctionalInterface
	public interface CustomKeyframeHandler<A extends GeoAnimatable> {
		void handle(CustomInstructionKeyframeEvent<A> event);
	}

	public enum State {
		RUNNING,
		TRANSITIONING,
		STOPPED;
	}
}