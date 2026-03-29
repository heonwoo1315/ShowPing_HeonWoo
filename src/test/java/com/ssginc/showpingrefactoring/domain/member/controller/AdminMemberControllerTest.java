package com.ssginc.showpingrefactoring.domain.member.controller;

import com.ssginc.showpingrefactoring.common.exception.CustomException;
import com.ssginc.showpingrefactoring.common.exception.ErrorCode;
import com.ssginc.showpingrefactoring.common.jwt.JwtFilter;
import com.ssginc.showpingrefactoring.common.jwt.JwtTokenProvider;
import com.ssginc.showpingrefactoring.common.util.MfaGuard;
import com.ssginc.showpingrefactoring.domain.member.dto.response.AdminMemberResponseDto;
import com.ssginc.showpingrefactoring.domain.member.entity.Member;
import com.ssginc.showpingrefactoring.domain.member.entity.MemberRole;
import com.ssginc.showpingrefactoring.domain.member.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컨트롤러 단위 테스트 전략:
 *  - 비즈니스 로직(Service)은 Service 테스트(AdminServiceTest)에서 검증
 *  - 여기서는 HTTP 요청/응답 매핑, 상태 코드, 응답 JSON 구조를 검증
 *  - 보안(MfaGuard)은 MockBean으로 제어하여 권한 흐름 검증
 */
@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false) // Security Filter 비활성화 (JWT, CSRF 제외)
@DisplayName("AdminController - 유저 조회 API")
class AdminMemberControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminService adminService;

    // SecurityConfig 의존 Bean들을 Mock으로 대체
    @MockBean
    private JwtFilter jwtFilter;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private MfaGuard mfaGuard;

    private AdminMemberResponseDto userDto;
    private AdminMemberResponseDto adminDto;

    @BeforeEach
    void setUp() {
        Member user = Member.builder()
                .memberNo(1L)
                .memberId("user01")
                .memberName("홍길동")
                .memberEmail("hong@test.com")
                .memberPhone("010-1111-2222")
                .memberAddress("서울시 강남구")
                .memberRole(MemberRole.ROLE_USER)
                .memberPoint(500L)
                .streamKey("stream-key-user")
                .build();

        Member admin = Member.builder()
                .memberNo(2L)
                .memberId("admin01")
                .memberName("관리자")
                .memberEmail("admin@test.com")
                .memberPhone("010-9999-0000")
                .memberAddress("서울시 종로구")
                .memberRole(MemberRole.ROLE_ADMIN)
                .memberPoint(0L)
                .streamKey("stream-key-admin")
                .build();

        userDto = new AdminMemberResponseDto(user);
        adminDto = new AdminMemberResponseDto(admin);
    }

    // -------------------------------------------------------
    // GET /api/admin/members
    // -------------------------------------------------------
    @Nested
    @DisplayName("GET /api/admin/members - 전체 유저 목록 조회")
    class GetMembers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("200 - 유저 목록을 페이지로 반환한다")
        void 유저_목록을_페이지로_반환한다() throws Exception {
            // given
            Page<AdminMemberResponseDto> page = new PageImpl<>(
                    List.of(userDto, adminDto), PageRequest.of(0, 10), 2);
            given(adminService.getMembers(any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/admin/members")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray())
                    .andExpect(jsonPath("$.content.length()").value(2))
                    .andExpect(jsonPath("$.content[0].memberId").value("user01"))
                    .andExpect(jsonPath("$.content[1].memberId").value("admin01"))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.totalPages").value(1));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("200 - 응답에 memberPassword 필드가 없다")
        void 응답에_memberPassword_필드가_없다() throws Exception {
            // given
            Page<AdminMemberResponseDto> page = new PageImpl<>(
                    List.of(userDto), PageRequest.of(0, 10), 1);
            given(adminService.getMembers(any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/admin/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].memberPassword").doesNotExist());
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("200 - 유저가 없으면 빈 페이지를 반환한다")
        void 유저가_없으면_빈_페이지를_반환한다() throws Exception {
            // given
            Page<AdminMemberResponseDto> emptyPage = new PageImpl<>(
                    List.of(), PageRequest.of(0, 10), 0);
            given(adminService.getMembers(any())).willReturn(emptyPage);

            // when & then
            mockMvc.perform(get("/api/admin/members"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // -------------------------------------------------------
    // GET /api/admin/members/{memberNo}
    // -------------------------------------------------------
    @Nested
    @DisplayName("GET /api/admin/members/{memberNo} - 단건 유저 조회")
    class GetMember {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("200 - 존재하는 memberNo로 조회하면 유저 정보를 반환한다")
        void 존재하는_memberNo로_조회하면_유저_정보를_반환한다() throws Exception {
            // given
            given(adminService.getMember(1L)).willReturn(userDto);

            // when & then
            mockMvc.perform(get("/api/admin/members/1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.memberNo").value(1))
                    .andExpect(jsonPath("$.memberId").value("user01"))
                    .andExpect(jsonPath("$.memberName").value("홍길동"))
                    .andExpect(jsonPath("$.memberEmail").value("hong@test.com"))
                    .andExpect(jsonPath("$.memberRole").value("ROLE_USER"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("404 - 존재하지 않는 memberNo로 조회하면 404를 반환한다")
        void 존재하지_않는_memberNo로_조회하면_404를_반환한다() throws Exception {
            // given
            given(adminService.getMember(999L))
                    .willThrow(new CustomException(ErrorCode.USER_NOT_FOUND));

            // when & then
            mockMvc.perform(get("/api/admin/members/999"))
                    .andExpect(status().isNotFound());
        }
    }

    // -------------------------------------------------------
    // GET /api/admin/members/search?keyword=
    // -------------------------------------------------------
    @Nested
    @DisplayName("GET /api/admin/members/search - 키워드 검색")
    class SearchMembers {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("200 - keyword 파라미터로 유저를 검색하여 반환한다")
        void keyword_파라미터로_유저를_검색하여_반환한다() throws Exception {
            // given
            Page<AdminMemberResponseDto> page = new PageImpl<>(
                    List.of(userDto), PageRequest.of(0, 10), 1);
            given(adminService.searchMembers(eq("user"), any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/admin/members/search")
                            .param("keyword", "user")
                            .param("page", "0")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content.length()").value(1))
                    .andExpect(jsonPath("$.content[0].memberId").value("user01"));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("200 - keyword가 없으면 전체 목록을 반환한다")
        void keyword가_없으면_전체_목록을_반환한다() throws Exception {
            // given
            Page<AdminMemberResponseDto> page = new PageImpl<>(
                    List.of(userDto, adminDto), PageRequest.of(0, 10), 2);
            given(adminService.searchMembers(eq(null), any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/admin/members/search"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(2));
        }

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("200 - 검색 결과가 없으면 빈 페이지를 반환한다")
        void 검색_결과가_없으면_빈_페이지를_반환한다() throws Exception {
            // given
            Page<AdminMemberResponseDto> emptyPage = new PageImpl<>(
                    List.of(), PageRequest.of(0, 10), 0);
            given(adminService.searchMembers(eq("없는키워드"), any())).willReturn(emptyPage);

            // when & then
            mockMvc.perform(get("/api/admin/members/search")
                            .param("keyword", "없는키워드"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isEmpty())
                    .andExpect(jsonPath("$.totalElements").value(0));
        }
    }

    // -------------------------------------------------------
    // 보안 검증 (MfaGuard 스텁 활용)
    // -------------------------------------------------------
    @Nested
    @DisplayName("보안 - MfaGuard 권한 검증")
    class Security {

        @Test
        @WithMockUser(roles = "ADMIN")
        @DisplayName("MfaGuard가 허용하면 200을 반환한다")
        void mfaGuard가_허용하면_200을_반환한다() throws Exception {
            // given: MFA 인증 완료 상태 스텁
            given(mfaGuard.decision(any())).willReturn(new AuthorizationDecision(true));
            Page<AdminMemberResponseDto> page = new PageImpl<>(
                    List.of(userDto), PageRequest.of(0, 10), 1);
            given(adminService.getMembers(any())).willReturn(page);

            // when & then
            mockMvc.perform(get("/api/admin/members"))
                    .andExpect(status().isOk());
        }
    }
}
