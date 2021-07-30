package rocks.spaghetti.headlessmc.game.auth;

import com.mojang.authlib.Agent;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.ProfileLookupCallback;
import net.minecraft.util.Util;
import rocks.spaghetti.headlessmc.game.GameClient;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;

public class AuthInfo {
    private String username;
    private UUID uuid;
    private String accessToken;
    private String accountType;

    private AuthInfo(String username, UUID uuid, String accessToken, String accountType) {
        this.username = username;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.accountType = accountType;
    }

    public String getUsername() {
        return username;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getAccountType() {
        return accountType;
    }

    public static AuthInfo offlineUser(GameClient client, String username) {
        CountDownLatch latch = new CountDownLatch(1);
        final UUID[] uuid = { Util.NIL_UUID };

        client.getAuthService().createProfileRepository().findProfilesByNames(new String[]{ username }, Agent.MINECRAFT, new ProfileLookupCallback() {
            @Override
            public void onProfileLookupSucceeded(GameProfile profile) {
                uuid[0] = profile.getId();
                latch.countDown();
            }

            @Override
            public void onProfileLookupFailed(GameProfile profile, Exception exception) {
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new AuthInfo(username, uuid[0], "", "legacy");
    }
}
