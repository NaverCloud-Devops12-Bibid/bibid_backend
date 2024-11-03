package bibid.service.specialAuction.impl;

import bibid.entity.*;
import bibid.repository.account.AccountRepository;
import bibid.repository.specialAuction.SpecialAuctionRepository;
import bibid.service.livestation.LiveStationPoolManager;
import bibid.repository.auction.AuctionRepository;
import bibid.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Component
@RequiredArgsConstructor
@Slf4j
public class SpecialAuctionScheduler {

    private final SimpMessagingTemplate messagingTemplate;
    private final AuctionRepository auctionRepository;
    private final TaskScheduler taskScheduler;
    private final LiveStationPoolManager liveStationPoolManager;
    private final NotificationService notificationService;
    private final Map<Long, Map<Long, ScheduledFuture<?>>> scheduledNotifications = new ConcurrentHashMap<>();
    private final AccountRepository accountRepository;

    // 경매 채널 할당 스케줄링
    public void scheduleChannelAllocation(Long auctionIndex, LocalDateTime startingLocalDateTime) {
//        LocalDateTime allocationTime = startingLocalDateTime.minusMinutes(30);

        // 5분 전으로 설정 (KST)
        LocalDateTime allocationTime = startingLocalDateTime.minusMinutes(30);

        Date scheduleDate = Date.from(allocationTime.atZone(ZoneId.systemDefault()).toInstant());

//        // KST를 UTC로 변환
//        ZonedDateTime allocationTimeUTC = allocationTimeKST.atZone(ZoneId.of("Asia/Seoul"))
//                .withZoneSameInstant(ZoneId.of("UTC"));
//
//        Date scheduleDate = Date.from(allocationTimeUTC.toInstant());


        ScheduledFuture<?> allocationTask = taskScheduler.schedule(() -> {
            try {
                log.info("채널 할당 작업 시작 for auctionIndex: {}", auctionIndex);
                LiveStationChannel allocatedChannel = liveStationPoolManager.allocateChannel();
                Auction auction = auctionRepository.findById(auctionIndex)
                        .orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다. ID: " + auctionIndex));

                auction.setAuctionStatus("준비중");
                auction.setLiveStationChannel(allocatedChannel);
                auctionRepository.save(auction);
                log.info("채널 할당 및 경매 상태 업데이트 성공 for auctionIndex: {}", auctionIndex);
            } catch (Exception e) {
                log.error("채널 할당 중 오류 발생 for auctionIndex: {}. 오류: {}", auctionIndex, e.getMessage(), e);
            }
        }, scheduleDate);

        log.info("경매 채널 할당 스케줄링 완료: auctionIndex={}, scheduleDate={}", auctionIndex, scheduleDate);
    }

    // 경매 종료 후 채널 반납 스케줄링
    public void scheduleChannelRelease(Long auctionIndex, LocalDateTime startingLocalDateTime) {
        // LocalDateTime releaseTime = startingLocalDateTime.plusHours(1);
        LocalDateTime releaseTime = startingLocalDateTime.plusMinutes(30);
        Date releaseDate = Date.from(releaseTime.atZone(ZoneId.systemDefault()).toInstant());

        taskScheduler.schedule(() -> {
            try {
                Auction auction = auctionRepository.findById(auctionIndex)
                        .orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다. ID: " + auctionIndex));
                LiveStationChannel channel = auction.getLiveStationChannel();
                if (channel == null) {
                    log.info("채널이 이미 반납된 상태입니다. 경매 ID: {}", auctionIndex);
                    return;
                }
                liveStationPoolManager.releaseChannel(channel);
                auction.setLiveStationChannel(null);
                auctionRepository.save(auction);
//                log.info("1시간 경과로 강제 채널 반납 완료: 경매 ID {}", auctionIndex);
                log.info("30분 경과로 강제 채널 반납 완료: 경매 ID {}", auctionIndex);
            } catch (Exception e) {
                log.error("채널 반납 중 오류 발생 for auctionIndex: {}. 오류: {}", auctionIndex, e.getMessage(), e);
            }
        }, releaseDate);

        log.info("경매 종료 후 채널 반납 스케줄링 완료: auctionIndex={}, releaseDate={}", auctionIndex, releaseDate);
    }

    // 경매 종료 스케줄링
    public void scheduleAuctionEnd(Long auctionIndex, LocalDateTime endingLocalDateTime) {
        Date endDate = Date.from(endingLocalDateTime.minusMinutes(4).atZone(ZoneId.systemDefault()).toInstant());
        log.info("스케줄링 설정: auctionIndex={}, 종료 시간={}", auctionIndex, endDate);

        taskScheduler.schedule(() -> {
            log.info("경매 종료 스케줄러 실행: auctionIndex={}", auctionIndex);
            try {
                // 경매 조회
                Auction auction = auctionRepository.findById(auctionIndex)
                        .orElseThrow(() -> new RuntimeException("경매를 찾을 수 없습니다. ID: " + auctionIndex));
                log.info("경매 조회 성공: auctionIndex={}", auctionIndex);

                // 경매 정보 중 마지막 입찰 정보 조회
                AuctionInfo lastBidInfo = auction.getAuctionInfoList()
                        .stream()
                        .max(Comparator.comparing(AuctionInfo::getBidTime))
                        .orElse(null);
                log.info("마지막 입찰 정보: {}", lastBidInfo != null ? "존재함" : "존재하지 않음");

                if (lastBidInfo != null) {
                    // 낙찰자 정보 설정
                    AuctionDetail auctionDetail = auction.getAuctionDetail();
                    auctionDetail.setWinnerIndex(lastBidInfo.getBidder().getMemberIndex());
                    auctionDetail.setWinningBid(lastBidInfo.getBidAmount());
                    auctionDetail.setWinnerNickname(lastBidInfo.getBidder().getNickname());
                    log.info("낙찰자 정보 설정 완료: winnerIndex={}, winningBid={}",
                            auctionDetail.getWinnerIndex(), auctionDetail.getWinningBid());

                    Member winningBidder = lastBidInfo.getBidder();
                    log.info("낙찰자 정보: memberIndex={}, nickname={}", winningBidder.getMemberIndex(), winningBidder.getNickname());

                    // 낙찰자의 계좌 정보 조회
                    Account winningBidderAccount = accountRepository.findByMember_MemberIndex(winningBidder.getMemberIndex())
                            .orElseThrow(() -> new RuntimeException("낙찰자의 계좌 정보를 찾을 수 없습니다."));
                    log.info("낙찰자 계좌 조회 성공: userMoney={}", winningBidderAccount.getUserMoney());

                    int winningBalance = Integer.parseInt(winningBidderAccount.getUserMoney());
                    int winningBidAmount = lastBidInfo.getBidAmount().intValue();
                    log.info("잔액 확인: winningBalance={}, winningBidAmount={}", winningBalance, winningBidAmount);

                    if (winningBalance < winningBidAmount) {
                        throw new RuntimeException("낙찰자의 잔액이 부족합니다.");
                    }

                    // 낙찰자의 계좌에서 금액 차감
                    winningBidderAccount.setUserMoney(String.valueOf(winningBalance - winningBidAmount));
                    accountRepository.save(winningBidderAccount);
                    log.info("낙찰자 계좌 차감 완료: 차감 후 잔액={}", winningBidderAccount.getUserMoney());

                    // 경매 상태를 '낙찰'로 설정
                    auction.setAuctionStatus("낙찰");

                    // 낙찰자와 판매자에게 알림 전송
                    notificationService.notifyAuctionWin(lastBidInfo.getBidder(), auctionIndex);
                    notificationService.notifyAuctionSold(auction.getMember(), auctionIndex);
                    log.info("알림 전송 완료: 낙찰자={}, 판매자={}", lastBidInfo.getBidder().getMemberIndex(), auction.getMember().getMemberIndex());

                } else {
                    auction.setAuctionStatus("유찰");
                    log.info("경매 유찰 처리: auctionIndex={}", auctionIndex);
                }

                auctionRepository.save(auction);
                log.info("경매 상태 저장 완료: auctionIndex={}, 상태={}", auctionIndex, auction.getAuctionStatus());

                sendAuctionEndDetails(auction);
                log.info("경매 종료 세부 정보 전송 완료: auctionIndex={}", auctionIndex);
            } catch (Exception e) {
                log.error("경매 종료 처리 중 오류 발생: 경매 ID {}. 오류: {}", auctionIndex, e.getMessage(), e);
            }
        }, endDate);

        log.info("경매 종료 스케줄링 완료: auctionIndex={}, endDate={}", auctionIndex, endDate);
    }


    public boolean registerAlarmForUser(Auction auction, Long memberIndex) {
        Long auctionIndex = auction.getAuctionIndex();
//        LocalDateTime auctionStartTime = auction.getStartingLocalDateTime().minusMinutes(30);
        LocalDateTime auctionStartTime = auction.getStartingLocalDateTime().minusMinutes(10);

        // 중복 스케줄링 확인
        if (scheduledNotifications.containsKey(auctionIndex) && scheduledNotifications.get(auctionIndex).containsKey(memberIndex)) {
            log.info("사용자가 이미 알림 신청을 완료했습니다. 경매 ID: {}, 사용자 ID: {}", auctionIndex, memberIndex);
            return false;
        }

        // DB에 알림을 "전송 예정" 상태로 저장
        Notification savedNotification = notificationService.createScheduledNotification(auction, memberIndex);
        Long notificationIndex = savedNotification.getNotificationIndex();


        // 예약된 시간에 알림 전송
        ScheduledFuture<?> scheduledTask = taskScheduler.schedule(
                () -> notificationService.sendAuctionStartNotificationToUser(auction, memberIndex, notificationIndex),
                Date.from(auctionStartTime.atZone(ZoneId.systemDefault()).toInstant())
        );

        scheduledNotifications.computeIfAbsent(auctionIndex, k -> new HashMap<>()).put(memberIndex, scheduledTask);
        log.info("알림 스케줄링 등록 완료: 경매 ID {}, 사용자 ID {}", auctionIndex, memberIndex);
        return true;
    }

    // WebSocket을 통해 경매 종료 세부 정보 전송
    private void sendAuctionEndDetails(Auction auction) {
        log.info("Sending auction end details for auction ID: {}", auction.getAuctionIndex());
        messagingTemplate.convertAndSend("/topic/auction/" + auction.getAuctionIndex(), auction.getAuctionDetail().toDto());
    }
}
