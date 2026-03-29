package com.ssginc.showpingrefactoring.domain.member.service;

import com.ssginc.showpingrefactoring.common.exception.CustomException;
import com.ssginc.showpingrefactoring.common.exception.ErrorCode;
import com.ssginc.showpingrefactoring.domain.member.dto.response.AdminMemberResponseDto;
import com.ssginc.showpingrefactoring.domain.member.entity.Member;
import com.ssginc.showpingrefactoring.domain.member.entity.MemberRole;
import com.ssginc.showpingrefactoring.domain.member.repository.MemberRepository;
import com.ssginc.showpingrefactoring.domain.member.service.implement.AdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService - 유저 조회")
class AdminServiceTest {

    @InjectMocks
    private AdminServiceImpl adminService;

    @Mock
    private MemberRepository memberRepository;

    private Member userMember;
    private Member adminMember;

    @BeforeEach
    void setUp() {
        userMember = Member.builder()
                .memberNo(1L)
                .memberId("user01")
                .memberName("홍길동")
                .memberEmail("hong@test.com")
                .memberPhone("010-1111-2222")
                .memberAddress("서울시 강남구")
                .memberRole(MemberRole.ROLE_USER)
                .memberPoint(500L)
                .build();

        adminMember = Member.builder()
                .memberNo(2L)
                .memberId("admin01")
                .memberName("관리자")
                .memberEmail("admin@test.com")
                .memberPhone("010-9999-0000")
                .memberAddress("서울시 종로구")
                .memberRole(MemberRole.ROLE_ADMIN)
                .memberPoint(0L)
                .build();
    }

    // -------------------------------------------------------
    // 전체 유저 목록 조회
    // -------------------------------------------------------
    @Nested
    @DisplayName("전체 유저 목록 조회")
    class GetMembers {

        @Test
        @DisplayName("유저가 존재하면 페이지 응답을 반환한다")
        void 유저가_존재하면_페이지_응답을_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> memberPage = new PageImpl<>(List.of(userMember, adminMember), pageable, 2);
            given(memberRepository.findAll(pageable)).willReturn(memberPage);

            // when
            Page<AdminMemberResponseDto> result = adminService.getMembers(pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getMemberId()).isEqualTo("user01");
            assertThat(result.getContent().get(1).getMemberId()).isEqualTo("admin01");
        }

        @Test
        @DisplayName("응답 DTO에 비밀번호가 포함되지 않는다")
        void 응답_DTO에_비밀번호가_포함되지_않는다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> memberPage = new PageImpl<>(List.of(userMember), pageable, 1);
            given(memberRepository.findAll(pageable)).willReturn(memberPage);

            // when
            Page<AdminMemberResponseDto> result = adminService.getMembers(pageable);

            // then
            // AdminMemberResponseDto에 getPassword() 메서드 자체가 없음을 컴파일 타임에 보장
            AdminMemberResponseDto dto = result.getContent().get(0);
            assertThat(dto.getMemberNo()).isEqualTo(1L);
            assertThat(dto.getMemberId()).isEqualTo("user01");
            assertThat(dto.getMemberRole()).isEqualTo(MemberRole.ROLE_USER);
            assertThat(dto.getMemberPoint()).isEqualTo(500L);
        }

        @Test
        @DisplayName("유저가 없으면 빈 페이지를 반환한다")
        void 유저가_없으면_빈_페이지를_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(memberRepository.findAll(pageable)).willReturn(emptyPage);

            // when
            Page<AdminMemberResponseDto> result = adminService.getMembers(pageable);

            // then
            assertThat(result.getTotalElements()).isZero();
            assertThat(result.getContent()).isEmpty();
        }

        @Test
        @DisplayName("페이지네이션 정보가 응답에 올바르게 반영된다")
        void 페이지네이션_정보가_올바르게_반영된다() {
            // given
            Pageable pageable = PageRequest.of(1, 5); // 2번째 페이지, 5개씩
            Page<Member> memberPage = new PageImpl<>(List.of(userMember), pageable, 6); // 총 6명
            given(memberRepository.findAll(pageable)).willReturn(memberPage);

            // when
            Page<AdminMemberResponseDto> result = adminService.getMembers(pageable);

            // then
            assertThat(result.getNumber()).isEqualTo(1);       // 현재 페이지
            assertThat(result.getSize()).isEqualTo(5);          // 페이지 크기
            assertThat(result.getTotalElements()).isEqualTo(6); // 전체 수
            assertThat(result.getTotalPages()).isEqualTo(2);    // 전체 페이지 수
        }
    }

    // -------------------------------------------------------
    // 단건 유저 조회
    // -------------------------------------------------------
    @Nested
    @DisplayName("단건 유저 조회")
    class GetMember {

        @Test
        @DisplayName("존재하는 memberNo로 조회하면 해당 유저를 반환한다")
        void 존재하는_memberNo로_조회하면_해당_유저를_반환한다() {
            // given
            given(memberRepository.findById(1L)).willReturn(Optional.of(userMember));

            // when
            AdminMemberResponseDto result = adminService.getMember(1L);

            // then
            assertThat(result.getMemberNo()).isEqualTo(1L);
            assertThat(result.getMemberId()).isEqualTo("user01");
            assertThat(result.getMemberName()).isEqualTo("홍길동");
            assertThat(result.getMemberEmail()).isEqualTo("hong@test.com");
        }

        @Test
        @DisplayName("존재하지 않는 memberNo로 조회하면 USER_NOT_FOUND 예외가 발생한다")
        void 존재하지_않는_memberNo로_조회하면_예외가_발생한다() {
            // given
            given(memberRepository.findById(999L)).willReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> adminService.getMember(999L))
                    .isInstanceOf(CustomException.class)
                    .hasMessage(ErrorCode.USER_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("단건 조회 시 repository의 findById가 정확히 한 번 호출된다")
        void 단건_조회_시_repository가_정확히_한번_호출된다() {
            // given
            given(memberRepository.findById(1L)).willReturn(Optional.of(userMember));

            // when
            adminService.getMember(1L);

            // then
            verify(memberRepository).findById(1L);
        }
    }

    // -------------------------------------------------------
    // 키워드 검색
    // -------------------------------------------------------
    @Nested
    @DisplayName("키워드 검색")
    class SearchMembers {

        @Test
        @DisplayName("키워드가 memberId에 매칭되는 유저를 반환한다")
        void 키워드가_memberId에_매칭되는_유저를_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> page = new PageImpl<>(List.of(userMember), pageable, 1);
            given(memberRepository.findByKeyword("user", pageable)).willReturn(page);

            // when
            Page<AdminMemberResponseDto> result = adminService.searchMembers("user", pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getMemberId()).isEqualTo("user01");
        }

        @Test
        @DisplayName("키워드가 memberName에 매칭되는 유저를 반환한다")
        void 키워드가_memberName에_매칭되는_유저를_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> page = new PageImpl<>(List.of(userMember), pageable, 1);
            given(memberRepository.findByKeyword("홍길동", pageable)).willReturn(page);

            // when
            Page<AdminMemberResponseDto> result = adminService.searchMembers("홍길동", pageable);

            // then
            assertThat(result.getContent().get(0).getMemberName()).isEqualTo("홍길동");
        }

        @Test
        @DisplayName("키워드가 memberEmail에 매칭되는 유저를 반환한다")
        void 키워드가_memberEmail에_매칭되는_유저를_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> page = new PageImpl<>(List.of(userMember), pageable, 1);
            given(memberRepository.findByKeyword("hong@test.com", pageable)).willReturn(page);

            // when
            Page<AdminMemberResponseDto> result = adminService.searchMembers("hong@test.com", pageable);

            // then
            assertThat(result.getContent().get(0).getMemberEmail()).isEqualTo("hong@test.com");
        }

        @Test
        @DisplayName("일치하는 키워드가 없으면 빈 페이지를 반환한다")
        void 일치하는_키워드가_없으면_빈_페이지를_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            given(memberRepository.findByKeyword("없는키워드", pageable)).willReturn(emptyPage);

            // when
            Page<AdminMemberResponseDto> result = adminService.searchMembers("없는키워드", pageable);

            // then
            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("키워드가 null이면 전체 목록을 반환한다")
        void 키워드가_null이면_전체_목록을_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> page = new PageImpl<>(List.of(userMember, adminMember), pageable, 2);
            given(memberRepository.findAll(pageable)).willReturn(page);

            // when
            Page<AdminMemberResponseDto> result = adminService.searchMembers(null, pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
        }

        @Test
        @DisplayName("키워드가 빈 문자열이면 전체 목록을 반환한다")
        void 키워드가_빈_문자열이면_전체_목록을_반환한다() {
            // given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Member> page = new PageImpl<>(List.of(userMember, adminMember), pageable, 2);
            given(memberRepository.findAll(pageable)).willReturn(page);

            // when
            Page<AdminMemberResponseDto> result = adminService.searchMembers("  ", pageable);

            // then
            assertThat(result.getTotalElements()).isEqualTo(2);
        }
    }
}
