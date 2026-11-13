import type { AiExplanation } from '../../api';
import RegulationList from './RegulationList';

/** STEP 1 AI 규정 설명: 공감 → 규정에 따른 이유 → 대안 → 근거 */
export default function ExplanationView({ explanation }: { explanation: AiExplanation }) {
  return (
    <div className="explain">
      <p className="explain__empathy">{explanation.empathy}</p>
      <p>{explanation.reason}</p>
      <div className="explain__alt">
        <span className="explain__label">이렇게 해 보세요</span>
        <p>{explanation.alternative}</p>
      </div>
      {explanation.citations.length > 0 ? (
        <div className="explain__cite">
          <span className="explain__label">근거 자료</span>
          <RegulationList items={explanation.citations} />
        </div>
      ) : (
        <p className="explain__none">이 내용과 맞는 학교 공개 규정은 찾지 못했어요.</p>
      )}
    </div>
  );
}
