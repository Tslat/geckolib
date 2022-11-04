/*
 * Copyright (c) 2020.
 * Author: Bernie G. (Gecko)
 */
package software.bernie.geckolib3.core.animatable;

import software.bernie.geckolib3.core.animatable.model.GeoModel;
import software.bernie.geckolib3.core.animation.AnimationController;
import software.bernie.geckolib3.core.manager.AnimationData;
import software.bernie.geckolib3.core.manager.AnimationFactory;

import java.util.function.Supplier;

/**
 * This is the root interface for all animatable objects in Geckolib.
 * Generally speaking you should use one of the sub-interfaces relevant to your specific object so that your model can be automatically handled.<br>
 * See:<br>
 * <ul>
 *     <li>{@link GeoBlock}</li>
 *     <li>{@link GeoEntity}</li>
 *     <li>{@link GeoItem}</li>
 * </ul>
 */
public interface GeoAnimatable {
	/**
	 * Register your {@link AnimationController AnimationControllers} and their respective animations and conditions.
	 * Override this method in your animatable object and add your controllers via {@link AnimationData#addAnimationController(AnimationController)}.
	 * You may add as many controllers as wanted.
	 * <br><br>
	 * Each controller can only play <u>one</u> animation at a time, and so animations that you intend to play concurrently should be handled in independent controllers.
	 * Note having multiple animations playing via multiple controllers can override parts of one animation with another if both animations use the same bones or child bones.
	 * @param data The object to register your controller instances to
	 */
	void registerControllers(AnimationData data);

	/**
	 * Each instance of a {@code GeoAnimatable} must return an instance of an {@link AnimationFactory}, which handles instance-specific animation info.
	 * Generally speaking, you should create your factory using {@link GeckoLibUtil#createFactory} and store it in your animatable instance, returning that cached instance when called.
	 * @return A cached instance of an {@code AnimationFactory}
	 */
	AnimationFactory getFactory();

	/**
	 * Animatables must be able to supply the {@link GeoModel} that their animatable object is relevant to.
	 * The supplier is important to allow custom implementations to not need sided code to be immediately present.
	 * @return A {@link Supplier} of a new or cached Geckolib model
	 */
	Supplier<? extends GeoModel> getGeoModel();
}
