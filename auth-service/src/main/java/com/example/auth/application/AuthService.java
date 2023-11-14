package com.example.auth.application;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.auth.application.dto.response.GetAuthorizedUriResponse;
import com.example.auth.application.dto.response.OAuthLoginResponse;
import com.example.auth.application.dto.response.OAuthUserResponse;
import com.example.auth.application.dto.response.RegisterResponse;
import com.example.auth.client.MemberServiceClient;
import com.example.auth.domain.AuthInfoRepository;
import com.example.auth.domain.OAuthInfo;
import com.example.auth.domain.OAuthType;
import com.example.auth.domain.member.Member;
import com.example.auth.domain.member.MemberRepository;
import com.example.auth.exception.ApiException;
import com.example.auth.exception.ErrorCode;
import com.example.auth.factory.RandomStringFactory;
import com.example.auth.jwt.JwtTokenProvider;
import com.example.auth.jwt.TokenInfo;
import com.example.auth.jwt.TokenInfoResponse;
import com.example.auth.oauth.OAuthProvider;
import com.example.auth.oauth.OAuthProviderResolver;
import com.example.auth.presentation.dto.request.RegisterRequest;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {
    private final OAuthProviderResolver oAuthProviderResolver;
    private final AuthInfoRepository authInfoRepository;
    private final MemberRepository memberRepository;
    private final RandomStringFactory randomStringFactory;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberServiceClient memberServiceClient;

    /**
     * 입력받은 providerName을 사용해 해당되는 OAuthProvider를 찾는다.
     * 반환된 OAuthProvider은 Authorized URL을 만들어 반환한다.
     */
    public GetAuthorizedUriResponse getAuthorizedUri(String providerName) {
        OAuthProvider oAuthProvider = oAuthProviderResolver.find(providerName);
        String state = randomStringFactory.create();
        return new GetAuthorizedUriResponse(oAuthProvider.getAuthorizedUriWithParams(state));
    }

    /**
     * 특정 Provider에게 받아온 Auth Code를 사용해 providerResponse를 가져온다. ex. google에게 받아온 Auth Code를 사용함
     * 기존에 등록된 적 없던 사용자면 DB에 저장해주고, 기존에 등록된 적 있는 사용자의 경우 해당 데이터를 불러온다.
     * 가져온 데이터를 사용해 TokenInfo를 생성한다.(access token, refresh token)
     * Member의 Refresh Token을 갱신한다.
     */
    @Transactional
    public OAuthLoginResponse login(String providerName, String authCode) {
        OAuthProvider oAuthProvider = oAuthProviderResolver.find(providerName);
        OAuthUserResponse providerResponse = oAuthProvider.getOAuthUserResponse(authCode);
        OAuthInfo oAuthInfo = retrieveOrCreateMemberUsingAuthCode(oAuthProvider.getOAuthType(), providerResponse);

        Long memberId = oAuthInfo.getMemberId();
        if (memberId == null) {
            oAuthInfo.setAuthorizedToken(randomStringFactory.create());
            return OAuthLoginResponse.createWithoutToken(oAuthInfo, providerResponse);
        }

        TokenInfo tokenInfo = jwtTokenProvider.generateTokenInfo(memberId, LocalDateTime.now());
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ApiException(ErrorCode.MEMBER_NOT_FOUND));
        member.changeRefreshToken(tokenInfo.getRefreshToken());
        return OAuthLoginResponse.create(oAuthInfo, tokenInfo, providerResponse);
    }

    /**
     * 서비스를 이용하기 위해 필요한 초기값을 입력받는다.
     * OAuth 방식으로 인증이 끝난 사용자는 profileUrl, name, email을 추가로 입력한다.
     * Member를 관리하는 MSA 서버에게 register 요청을 보낸다.
     * 요청이 성공하면 OAuthInfo의 registered 필드를 true로 변경하고 응답을 반환한다
     */
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
//        OAuthInfo info = authInfoRepository.findById(request.getAuthId())
//            .orElseThrow(() -> new ApiException(ErrorCode.AUTH_INFO_NOT_FOUND));
//        if (!info.validateAuthorizedToken(request.getAuthorizedToken())) {
//            throw new ApiException(ErrorCode.TOKEN_UNAUTHORIZED);
//        }
//
//        MemberRegisterRequest requestUsingMemberService =
//            MemberRegisterRequest.create(request.getProfileUrl(), request.getName(), info.getEmail());
//        MemberRegisterResponse response = memberServiceClient.register(requestUsingMemberService);
//        info.init(response.getId());
//
//        TokenInfo tokenInfo = jwtTokenProvider.generateTokenInfo(response.getId(), LocalDateTime.now());
//
//        info.changeRefreshToken(tokenInfo.getRefreshToken());
//        return RegisterResponse.create(tokenInfo, info.getMemberId());
        return null;
    }

    /**
     * 리프레쉬 토큰을 사용해 TokenInfo를 재발급한다.
     * 만약 리프레쉬 토큰이 잘못되었거나, 만료되었다면 예외를 반환한다.
     */
    @Transactional
    public TokenInfo refreshToken(String refreshToken) {
        TokenInfoResponse refreshTokenInfo = jwtTokenProvider.parse(refreshToken);
        Long memberId = refreshTokenInfo.getId();
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new ApiException(ErrorCode.SERVER_ERROR));
        if (!member.isRefreshTokenMatching(refreshToken)) {
            throw new ApiException(ErrorCode.TOKEN_ID_INVALID);
        }
        TokenInfo generatedTokenInfo = jwtTokenProvider.generateTokenInfo(memberId, LocalDateTime.now());
        member.changeRefreshToken(generatedTokenInfo.getRefreshToken());

        return generatedTokenInfo;
    }

    /**
     * 입력된 토큰이 적절한지 파싱한다.
     */
    public TokenInfoResponse parse(String token) {
        return jwtTokenProvider.parse(token);
    }


    private OAuthInfo retrieveOrCreateMemberUsingAuthCode(OAuthType type, OAuthUserResponse response) {
        Optional<OAuthInfo> oAuthInfoOpt = authInfoRepository.findByIdUsingResourceServerAndType(
            response.getIdUsingResourceServer(), type);

        if (oAuthInfoOpt.isEmpty()) {
            OAuthInfo oAuthInfo = response.toEntity(type);
            return authInfoRepository.save(oAuthInfo);
        }
        return oAuthInfoOpt.get();
    }
}
