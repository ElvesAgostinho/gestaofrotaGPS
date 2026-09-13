package ao.autocare.modules.mobile;

import java.util.List;

public final class MobileDtos {

    private MobileDtos() {}

    /** Uma viatura para escolher no telemovel; «mine» = atribuida a quem esta a ver. */
    public record AssetPick(String id, String tag, String name, String plate, String status, boolean mine) {}

    public record HomeView(String userName, List<AssetPick> assets, long myOpenOrders, boolean hasAssignedAssets) {}
}
