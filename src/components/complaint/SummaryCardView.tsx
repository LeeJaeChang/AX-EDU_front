import { useState, type ReactNode } from 'react';
import type { CardSuggestion, EmotionLevel, SummaryCard, UrgencyLevel } from '../../api';
import { CONTACT_LABEL, EMOTION_LABEL, EMOTION_TREND_LABEL, TAG_LABEL, URGENCY_LABEL } from '../../lib/complaint';
import { formatDateTime } from '../../lib/format';
import RegulationList from './RegulationList';

const URGENCY_TONE: Record<UrgencyLevel, string> = { URGENT: 'pill--warn', SOON: 'pill--mark', NORMAL: 'pill--neutral' };
const EMOTION_TONE: Record<EmotionLevel, string> = { AGITATED: 'pill--warn', CONCERNED: 'pill--mark', CALM: 'pill--ok' };

const FIELD_LABEL: Record<CardSuggestion['field'], string> = { situation: '상황', concerns: '걱정하는 점', requests: '요청 사항' };

interface Props {
  card: SummaryCard;
  /** 채팅 말풍선용 축약 보기 (펼치기 가능) */
  compact?: boolean;
  suggestions?: CardSuggestion[];
  onApplySuggestion?: (s: CardSuggestion) => void;
  busy?: boolean;
  /** 카드 맨 아래(‘카드 전체 보기’ 바로 밑)에 붙는 행동 버튼 */
  footer?: ReactNode;
}

/** 요약 카드 (명세서 3.7). 학부모와 교사가 같은 카드를 본다. */
export default function SummaryCardView({ card, compact = false, suggestions, onApplySuggestion, busy, footer }: Props) {
  const [open, setOpen] = useState(!compact);
  const { basicInfo, preferredContact, urgency, parentEmotion: emotion } = card;

  return (
    <article className={`scard ${compact ? 'scard--compact' : ''} ${urgency?.level === 'URGENT' ? 'scard--urgent' : ''}`} aria-label="민원 요약 카드">
      <header className="scard__head">
        <div className="scard__title-row">
          <span className="scard__eyebrow">민원 요약 카드</span>
          <span className="scard__ver tnum">v{card.version}</span>
        </div>
        <p className="scard__meta tnum">
          {basicInfo.grade}학년 {basicInfo.classNo}반 · 접수 {formatDateTime(basicInfo.receivedAt)}
        </p>
        <div className="chips">
          {/* 빠른 대응 여부와 학부모 마음 상태는 접힌 카드에서도 먼저 보이게 */}
          {urgency && urgency.level !== 'NORMAL' && (
            <span className={`pill ${URGENCY_TONE[urgency.level]}`}>{URGENCY_LABEL[urgency.level]}</span>
          )}
          {emotion && emotion.level !== 'CALM' && (
            <span className={`pill ${EMOTION_TONE[emotion.level]}`}>학부모 {EMOTION_LABEL[emotion.level]}</span>
          )}
          {basicInfo.tags.map((t) => (
            <span key={t} className="pill pill--accent">{TAG_LABEL[t]}</span>
          ))}
        </div>
      </header>

      {open && (
        <>
          <section className="scard__sec">
            <h4 className="scard__label">상황</h4>
            <p className="scard__attr">{card.attribution}</p>
            <ol className="steps">
              {card.situation.map((s) => (
                <li key={s.order} className="steps__item">
                  {[s.when, s.where].some((x) => x && x !== '—') && (
                    <span className="steps__when">{[s.when, s.where].filter((x) => x && x !== '—').join(' · ')}</span>
                  )}
                  <p className="steps__what">{s.what}</p>
                </li>
              ))}
            </ol>
          </section>

          {urgency && (
            <section className="scard__sec scard__sec--row">
              <h4 className="scard__label">대응 시급도</h4>
              <div className="chips">
                <span className={`pill ${URGENCY_TONE[urgency.level]}`}>{URGENCY_LABEL[urgency.level]}</span>
                {urgency.reason && <span className="pill pill--neutral">{urgency.reason}</span>}
              </div>
            </section>
          )}

          {emotion && (
            <section className="scard__sec scard__emotion">
              <h4 className="scard__label">학부모 마음 상태</h4>
              <div className="chips">
                <span className={`pill ${EMOTION_TONE[emotion.level]}`}>{EMOTION_LABEL[emotion.level]}</span>
                {emotion.trend !== 'STEADY' && <span className="pill pill--neutral">{EMOTION_TREND_LABEL[emotion.trend]}</span>}
              </div>
              <p className="scard__emotion-text">{emotion.summary}</p>
              {emotion.signals.length > 0 && (
                <ul className="scard__signals" aria-label="감정 신호">
                  {emotion.signals.map((x) => (
                    <li key={x}>{x}</li>
                  ))}
                </ul>
              )}
              <p className="scard__hint">AI가 상담 대화를 보고 정리했어요. 학부모님이 쓰신 원문은 전달되지 않아요.</p>
            </section>
          )}

          {card.concerns.length > 0 && (
            <section className="scard__sec">
              <h4 className="scard__label">학생에게 미친 영향</h4>
              <ul className="scard__list">
                {card.concerns.map((c, i) => (
                  <li key={i}>{c}</li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      <section className="scard__sec">
        <h4 className="scard__label">요청 사항</h4>
        <ol className="scard__list scard__list--num">
          {card.requests.map((r, i) => (
            <li key={i}>{r}</li>
          ))}
        </ol>
      </section>

      {open && (
        <>
          <section className="scard__sec scard__sec--row">
            <h4 className="scard__label">희망 연락</h4>
            <div className="chips">
              {preferredContact.methods.map((m) => (
                <span key={m} className="pill pill--neutral">{CONTACT_LABEL[m]}</span>
              ))}
              <span className="pill pill--neutral tnum">
                {preferredContact.timeWindow ? `${preferredContact.timeWindow.from}~${preferredContact.timeWindow.to}` : '시간 무관'}
              </span>
            </div>
          </section>

          {card.regulations.length > 0 && (
            <section className="scard__sec">
              <h4 className="scard__label">관련 규정</h4>
              <RegulationList items={card.regulations} />
            </section>
          )}

          {suggestions && suggestions.length > 0 && (
            <section className="scard__sec scard__suggest" aria-live="polite">
              <h4 className="scard__label">AI 순화 제안</h4>
              <p className="scard__hint">감정이 담긴 표현이 있어요. 선생님께 더 잘 전달되도록 이렇게 바꿔 볼까요?</p>
              <ul className="suggest-list">
                {suggestions.map((s) => (
                  <li key={`${s.field}-${s.index}`} className="suggest">
                    <span className="suggest__field">{FIELD_LABEL[s.field]}</span>
                    <p className="suggest__from">{s.original}</p>
                    <p className="suggest__to">{s.suggested}</p>
                    {onApplySuggestion && (
                      <button type="button" className="btn btn--soft btn--xs" disabled={busy} onClick={() => onApplySuggestion(s)}>
                        이렇게 바꾸기
                      </button>
                    )}
                  </li>
                ))}
              </ul>
            </section>
          )}
        </>
      )}

      {card.confirmedByParentAt && open && (
        <footer className="scard__foot">학부모님이 {formatDateTime(card.confirmedByParentAt)}에 확인하고 보낸 카드입니다.</footer>
      )}

      {compact && (
        <button type="button" className="scard__toggle" onClick={() => setOpen((v) => !v)} aria-expanded={open}>
          {open ? '접기' : '카드 전체 보기'}
        </button>
      )}
      {footer && <div className="scard__action">{footer}</div>}
    </article>
  );
}
