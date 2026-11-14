import { useCallback, useEffect, useState } from 'react';
import { complaintApi, realtime, type Complaint } from '../api';

/** GET /complaints/{id} + complaint.status_changed · complaint.card_updated 수신 시 다시 불러오기 */
export function useComplaint(id: string | null | undefined) {
  const [complaint, setComplaint] = useState<Complaint | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(!!id);

  const reload = useCallback(async () => {
    if (!id) return;
    try {
      setComplaint(await complaintApi.get(id));
      setError(null);
    } catch (e) {
      setError((e as Error).message);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    // 방금 만든 민원을 이미 들고 있으면 비우지 않는다 (깜빡임 방지)
    setComplaint((c) => (c && c.id === id ? c : null));
    setLoading(!!id);
    void reload();
  }, [reload, id]);

  useEffect(() => {
    if (!id) return;
    return realtime.subscribe((e) => {
      if ((e.event === 'complaint.status_changed' || e.event === 'complaint.card_updated') && e.data.complaintId === id) {
        void reload();
      }
    });
  }, [id, reload]);

  return { complaint, setComplaint, error, loading, reload };
}
