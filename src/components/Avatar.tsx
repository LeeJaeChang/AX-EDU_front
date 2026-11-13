import { initialOf } from '../lib/format';
import { MegaphoneIcon, PersonIcon } from './Icons';

interface Props {
  name: string;
  imageUrl?: string | null;
  /** placeholder: 회색 실루엣(목록) · initial: 오렌지 이니셜(상세 헤더) · notice: 공지형 계정 */
  variant?: 'placeholder' | 'initial' | 'notice';
  size?: number;
}

export default function Avatar({ name, imageUrl, variant = 'placeholder', size = 48 }: Props) {
  const style = { width: size, height: size, fontSize: size * 0.4 };
  if (imageUrl) {
    return <img className="avatar" src={imageUrl} alt="" style={style} />;
  }
  if (variant === 'notice') {
    return (
      <span className="avatar avatar--notice" style={style} aria-hidden="true">
        <MegaphoneIcon width={size * 0.5} height={size * 0.5} />
      </span>
    );
  }
  if (variant === 'initial') {
    return (
      <span className="avatar avatar--initial" style={style} aria-hidden="true">
        {initialOf(name)}
      </span>
    );
  }
  return (
    <span className="avatar avatar--placeholder" style={style} aria-hidden="true">
      <PersonIcon width={size * 0.62} height={size * 0.62} />
    </span>
  );
}
