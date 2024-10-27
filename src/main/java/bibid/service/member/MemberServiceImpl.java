package bibid.service;

import bibid.dto.MemberDto;
import bibid.entity.Member;
import bibid.jwt.JwtProvider;
import bibid.repository.member.MemberRepository;
import bibid.service.member.MemberService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MemberServiceImpl implements MemberService {
    private final PasswordEncoder passwordEncoder;
    private final MemberRepository memberRepository;
    private final JwtProvider jwtProvider;
    private Optional<Member> optionalMember = Optional.empty();

    @Override
    public Map<String, String> memberIdCheck(String memberId) {
        Map<String, String> memberIdCheckMsgMap = new HashMap<>();

        long memberIdCheck = memberRepository.countByMemberId(memberId);

        if(memberIdCheck == 0)
            memberIdCheckMsgMap.put("memberIdCheckMsg", "available memberId");
        else
            memberIdCheckMsgMap.put("memberIdCheckMsg", "invalid memberId");

        return memberIdCheckMsgMap;
    }

    @Override
    public Map<String, String> nicknameCheck(String nickname) {
        Map<String, String> nicknameCheckMsgMap = new HashMap<>();

        long nicknameCheck = memberRepository.countByNickname(nickname);

        if(nicknameCheck == 0)
            nicknameCheckMsgMap.put("nicknameCheckMsg", "available nickname");
        else
            nicknameCheckMsgMap.put("nicknameCheckMsg", "invalid nickname");

        return nicknameCheckMsgMap;
    }

    @Override
    public MemberDto join(MemberDto memberDto) {
        memberDto.setRole("ROLE_USER");
        memberDto.setMemberPw(passwordEncoder.encode(memberDto.getMemberPw()));

        MemberDto joinedMemberDto = memberRepository.save(memberDto.toEntity()).toDto();

        joinedMemberDto.setMemberPw("");

        return joinedMemberDto;
    }

    @Override
    public MemberDto login(MemberDto memberDto) {
        Member member = memberRepository.findByMemberId(memberDto.getMemberId()).orElseThrow(
                () -> new RuntimeException("memberId not exist")
        );

        if(!passwordEncoder.matches(memberDto.getMemberPw(), member.getMemberPw())) {
            throw new RuntimeException("wrong memberPw");
        }

        MemberDto loginMemberDto = member.toDto();

        loginMemberDto.setMemberPw("");
        loginMemberDto.setToken(jwtProvider.createJwt(member));

        return loginMemberDto;
    }

    @Override
    public String findByEmail(String email) {
        Member member = memberRepository.findByEmail(email);

        if (member != null) {
            return member.getMemberId();
        } else {
            System.out.println("이메일에 해당하는 사용자가 없습니다.");
            return null;
        }
    }

    @Override
    public String modifyPasswd(String newPasswd) {

        if (optionalMember.isPresent()) {
            Member member = optionalMember.get();
            member.setMemberPw(passwordEncoder.encode(newPasswd));

            memberRepository.save(member);
            return "비밀번호 변경을 완료했습니다.";
        } else {
            System.out.println("이메일에 해당하는 사용자가 없습니다.");
            return null;
        }
    }

}