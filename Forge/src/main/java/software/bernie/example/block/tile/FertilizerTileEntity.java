package software.bernie.example.block.tile;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.example.registry.TileRegistry;
import software.bernie.geckolib3.core.animatable.GeoAnimatable;
import software.bernie.geckolib3.core.object.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType.EDefaultLoopTypes;
import software.bernie.geckolib3.core.animation.AnimationController;
import software.bernie.geckolib3.core.animation.AnimationEvent;
import software.bernie.geckolib3.core.animation.AnimationData;
import software.bernie.geckolib3.core.animation.factory.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

public class FertilizerTileEntity extends BlockEntity implements GeoAnimatable {
	private final AnimationFactory manager = GeckoLibUtil.createFactory(this);

	private <E extends BlockEntity & GeoAnimatable> PlayState predicate(AnimationEvent<E> event) {
		AnimationController controller = event.getController();
		controller.transitionLengthTicks = 0;
		if (event.getAnimatable().getLevel().isRaining()) {
			controller.setAnimation(new AnimationBuilder().addAnimation("fertilizer.animation.deploy", EDefaultLoopTypes.LOOP)
					.addAnimation("fertilizer.animation.idle", true));
		} else {
			controller.setAnimation(new AnimationBuilder().addAnimation("Botarium.anim.deploy", EDefaultLoopTypes.LOOP)
					.addAnimation("Botarium.anim.idle", true));
		}
		return PlayState.CONTINUE;
	}

	public FertilizerTileEntity(BlockPos pos, BlockState state) {
		super(TileRegistry.FERTILIZER.get(), pos, state);
	}

	@Override
	public void registerControllers(AnimationData data) {
		data.addAnimationController(new AnimationController(this, "controller", 0, this::predicate));
	}

	@Override
	public AnimationFactory getFactory() {
		return this.manager;
	}
}
