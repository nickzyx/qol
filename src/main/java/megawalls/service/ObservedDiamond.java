package megawalls.service;

import megawalls.domain.DiamondGear;

enum ObservedDiamond {
    HELMET(DiamondGear.HELMET),
    CHESTPLATE(DiamondGear.CHESTPLATE),
    LEGGINGS(DiamondGear.LEGGINGS),
    BOOTS(DiamondGear.BOOTS),
    SWORD(DiamondGear.SWORD);

    final DiamondGear gear;

    ObservedDiamond(DiamondGear gear) {
        this.gear = gear;
    }
}
