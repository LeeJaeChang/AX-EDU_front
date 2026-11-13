import { Link } from 'react-router-dom';
import type { ChatRoom } from '../api';
import { formatListTime } from '../lib/format';
import Avatar from './Avatar';
import { BellOffIcon } from './Icons';

export default function RoomListItem({ room }: { room: ChatRoom }) {
  const unread = room.unreadCount ?? 0;
  return (
    <li>
      <Link to={`/chat/${room.id}`} className="room-item">
        <Avatar
          name={room.title}
          imageUrl={room.counterpart?.profileImageUrl}
          variant={room.type === 'NOTICE' ? 'notice' : 'placeholder'}
          size={56}
        />
        <div className="room-item__body">
          <div className="room-item__title-row">
            <strong className="room-item__title">{room.title}</strong>
            {room.type === 'NOTICE' && <span className="pill pill--accent">공지형</span>}
            {room.availableHours && (
              <span className="pill pill--neutral tnum">
                {room.availableHours.from}~{room.availableHours.to}
              </span>
            )}
            {room.activeComplaintId && <span className="pill pill--warn">민원</span>}
            {room.muted && <BellOffIcon className="room-item__muted" width={14} height={14} aria-label="알림 꺼짐" />}
          </div>
          <p className="room-item__preview">{room.lastMessage?.preview ?? ''}</p>
        </div>
        <div className="room-item__meta">
          {room.lastMessage && (
            <time className="room-item__time tnum" dateTime={room.lastMessage.sentAt}>
              {formatListTime(room.lastMessage.sentAt)}
            </time>
          )}
          {unread > 0 && (
            <span className="unread tnum" aria-label={`안 읽은 메시지 ${unread}개`}>
              {unread > 99 ? '99+' : unread}
            </span>
          )}
        </div>
      </Link>
    </li>
  );
}
