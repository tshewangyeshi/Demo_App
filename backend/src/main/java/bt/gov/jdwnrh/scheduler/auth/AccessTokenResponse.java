package bt.gov.jdwnrh.scheduler.auth;

/** The refresh token itself never appears in a JSON body — it only ever travels as the httpOnly cookie. */
public record AccessTokenResponse(String accessToken, String tokenType) {

    public static AccessTokenResponse bearer(String accessToken) {
        return new AccessTokenResponse(accessToken, "Bearer");
    }
}
