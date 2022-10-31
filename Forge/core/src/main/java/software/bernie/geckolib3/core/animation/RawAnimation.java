/*
 * Copyright (c) 2020.
 * Author: Bernie G. (Gecko)
 */

package software.bernie.geckolib3.core.animation;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Objects;

/**
 * A builder class for a raw/unbaked animation. These are constructed to pass to the
 * {@link software.bernie.geckolib3.core.controller.AnimationController} to build into full-fledged animations for usage.
 * <br><br>
 * Animations added to this builder are added <u>in order of insertion</u> - the animations will play in the order that you define them.<br>
 * RawAnimation instances should be cached statically where possible to reduce overheads and improve efficiency.
 * <br><br>
 * Example usage: <br>
 * <code>RawAnimation.begin().thenPlay("action.open_box").thenLoop("state.stay_open")</code>
 */
public final class RawAnimation {
	private final List<Stage> animationList = new ObjectArrayList<>();

	// Private constructor to force usage of factory for logical operations
	private RawAnimation() {}

	/**
	 * Start a new RawAnimation instance. This is the start point for creating an animation chain.
	 * @return A new RawAnimation instance
	 */
	public static RawAnimation begin() {
		return new RawAnimation();
	}

	/**
	 * Append an animation to the animation chain, playing the named animation and stopping or progressing to the next chained animation
	 * @param animationName The name of the animation to play once
	 */
	public RawAnimation thenPlay(String animationName) {
		return then(animationName, Animation.LoopType.PLAY_ONCE);
	}

	/**
	 * Append an animation to the animation chain, playing the named animation and repeating it continuously until the animation is stopped by external sources.
	 * @param animationName The name of the animation to play on a loop
	 */
	public RawAnimation thenLoop(String animationName) {
		return then(animationName, Animation.LoopType.LOOP);
	}

	/**
	 * Append an animation to the animation chain, playing the named animation <code>playCount</code> times, then stopping or progressing to the next chained animation
	 * @param animationName The name of the animation to play X times
	 * @param playCount The number of times to repeat the animation before proceeding
	 */
	public RawAnimation thenPlayXTimes(String animationName, int playCount) {
		for (int i = 0; i < playCount; i++) {
			thenPlay(animationName);
		}

		return this;
	}

	/**
	 * Append an animation to the animation chain, playing the named animation and proceeding based on the <code>loopType</code> parameter provided.
	 * @param animationName The name of the animation to play. <u>MUST</u> match the name of the animation in the <code>.animation.json</code> file.
	 * @param loopType
	 * @return
	 */
	public RawAnimation then(String animationName, Animation.LoopType loopType) {
		this.animationList.add(new Stage(animationName, loopType));

		return this;
	}

	public List<Stage> getAnimationStages() {
		return this.animationList;
	}

	/**
	 * Create a new RawAnimation instance based on an existing RawAnimation instance.
	 * The new instance will be a shallow copy of the other instance, and can then be appended to or otherwise modified
	 * @param other The existing RawAnimation instance to copy
	 * @return A new instance of RawAnimation
	 */
	public static RawAnimation copyOf(RawAnimation other) {
		RawAnimation newInstance = RawAnimation.begin();

		newInstance.animationList.addAll(other.animationList);

		return newInstance;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;

		if (obj == null || this.getClass() != obj.getClass())
			return false;

		return this.hashCode() == obj.hashCode();
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.animationList);
	}

	/**
	 * An animation stage for a {@link RawAnimation} builder.<br>
	 * This is a single animation and loop pair representing a single animation stage of the final compiled animation.
	 */
	public record Stage(String animationName, Animation.LoopType loopType) {
		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;

			if (obj == null || this.getClass() != obj.getClass())
				return false;

			return this.hashCode() == obj.hashCode();
		}

		@Override
		public int hashCode() {
			return Objects.hash(this.animationName, this.loopType);
		}
	}
}
