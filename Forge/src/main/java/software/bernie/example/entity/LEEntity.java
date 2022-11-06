package software.bernie.example.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib3.animatable.GeoEntity;
import software.bernie.geckolib3.core.animatable.GeoAnimatable;
import software.bernie.geckolib3.core.IAnimationTickable;
import software.bernie.geckolib3.core.object.PlayState;
import software.bernie.geckolib3.core.builder.AnimationBuilder;
import software.bernie.geckolib3.core.builder.ILoopType.EDefaultLoopTypes;
import software.bernie.geckolib3.core.animation.AnimationController;
import software.bernie.geckolib3.core.animation.AnimationEvent;
import software.bernie.geckolib3.core.animation.AnimationData;
import software.bernie.geckolib3.core.animation.factory.AnimationFactory;
import software.bernie.geckolib3.util.GeckoLibUtil;

public class LEEntity extends PathfinderMob implements GeoEntity {
    private AnimationFactory factory = GeckoLibUtil.createFactory(this);

    private <E extends GeoAnimatable> PlayState predicate(AnimationEvent<E> event) {
            event.getController().setAnimation(new AnimationBuilder().addAnimation("animation.geoLayerEntity.idle", EDefaultLoopTypes.LOOP));
            return PlayState.CONTINUE;
    }

    public LEEntity(EntityType<? extends PathfinderMob> type, Level worldIn) {
        super(type, worldIn);
    }

	@Override
    public void registerControllers(AnimationData data) {
        data.addAnimationController(new AnimationController<LEEntity>(this, "controller", 5, this::predicate));
    }

    @Override
    public AnimationFactory getFactory() {
        return this.factory;
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        super.registerGoals();
    }

    @Override
    public int tickTimer() {
        return tickCount;
    }
}
