package com.dangtools.mixin;

import com.dangtools.Registration;
import com.dangtools.client.DangCreativeTabSections;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Set;

/**
 * 把本模组页签的内容换成「按分区构建」（每个分区先占一行空白留给横幅）。
 * 其它页签不受影响。
 */
@Mixin(CreativeModeTab.class)
public abstract class CreativeModeTabMixin {

    @Shadow
    private Collection<ItemStack> displayItems;

    @Shadow
    private Set<ItemStack> displayItemsSearchTab;

    @Inject(method = "buildContents", at = @At("HEAD"), cancellable = true)
    private void dangtools$buildContents(CreativeModeTab.ItemDisplayParameters parameters, CallbackInfo ci) {
        if ((Object) this != Registration.REVOLUTION_TAB.get()) {
            return;
        }
        LinkedList<ItemStack> items = new LinkedList<>();
        LinkedHashSet<ItemStack> searchItems = new LinkedHashSet<>();
        DangCreativeTabSections.buildContents(items::add, searchItems::add);
        this.displayItems = items;
        this.displayItemsSearchTab = searchItems;
        ci.cancel();
    }
}
