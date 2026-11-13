import type { Regulation } from '../../api';
import { SOURCE_LABEL } from '../../lib/complaint';

/** 근거 자료 — 학교 공개 자료만 (명세서 3.8) */
export default function RegulationList({ items }: { items: Regulation[] }) {
  if (items.length === 0) return null;
  return (
    <ul className="regs">
      {items.map((r) => (
        <li key={r.id} className="reg">
          <div className="reg__head">
            <span className={`reg__src reg__src--${r.sourceType.toLowerCase()}`}>{SOURCE_LABEL[r.sourceType]}</span>
            <strong className="reg__title">{r.title}</strong>
          </div>
          <p className="reg__excerpt">{r.excerpt}</p>
          {r.documentUrl && (
            <a className="reg__link" href={r.documentUrl} target="_blank" rel="noreferrer">
              원문 보기
            </a>
          )}
        </li>
      ))}
    </ul>
  );
}
