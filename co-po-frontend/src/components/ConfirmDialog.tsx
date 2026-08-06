import { useCallback, useRef, useState } from 'react';
import {
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
} from '@mui/material';

type ConfirmColor = 'primary' | 'error' | 'warning';

type ConfirmOptions = {
  title?: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmColor?: ConfirmColor;
};

type ConfirmState = {
  open: boolean;
  message: string;
  options: ConfirmOptions;
};

const defaultState: ConfirmState = { open: false, message: '', options: {} };

// Promise-based replacement for window.confirm(): `await confirm('Are you sure?')`
// resolves true/false the same way, but renders a themed MUI dialog instead of the
// browser's native confirm box.
export const useConfirmDialog = () => {
  const [state, setState] = useState<ConfirmState>(defaultState);
  const resolver = useRef<((value: boolean) => void) | null>(null);

  const confirm = useCallback((message: string, options: ConfirmOptions = {}) => {
    setState({ open: true, message, options });
    return new Promise<boolean>((resolve) => {
      resolver.current = resolve;
    });
  }, []);

  const handleClose = (result: boolean) => {
    setState((s) => ({ ...s, open: false }));
    resolver.current?.(result);
    resolver.current = null;
  };

  const ConfirmDialog = (
    <Dialog open={state.open} onClose={() => handleClose(false)} maxWidth="xs" fullWidth>
      <DialogTitle>{state.options.title ?? 'Please Confirm'}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ whiteSpace: 'pre-line', color: 'inherit' }}>
          {state.message}
        </DialogContentText>
      </DialogContent>
      <DialogActions>
        <Button onClick={() => handleClose(false)}>{state.options.cancelLabel ?? 'Cancel'}</Button>
        <Button
          onClick={() => handleClose(true)}
          variant="contained"
          color={state.options.confirmColor ?? 'primary'}
          autoFocus
        >
          {state.options.confirmLabel ?? 'Confirm'}
        </Button>
      </DialogActions>
    </Dialog>
  );

  return { confirm, ConfirmDialog };
};
