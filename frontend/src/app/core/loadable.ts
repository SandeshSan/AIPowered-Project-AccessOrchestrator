import { Observable, catchError, map, of, startWith } from 'rxjs';

import { errorMessage } from './format';

/** Loading / data / error state for a page section. */
export interface Loadable<T> {
  loading: boolean;
  data?: T;
  error?: string;
}

export const LOADING: Loadable<never> = { loading: true };

/** Wraps a request so templates can render loading, error and data states without extra flags. */
export function loadable<T>(source: Observable<T>, showLoading = true): Observable<Loadable<T>> {
  const wrapped = source.pipe(
    map(data => ({ loading: false, data }) as Loadable<T>),
    catchError(err => of({ loading: false, error: errorMessage(err) } as Loadable<T>)),
  );
  return showLoading ? wrapped.pipe(startWith(LOADING as Loadable<T>)) : wrapped;
}
