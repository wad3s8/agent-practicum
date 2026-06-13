import styles from '../AnalyticsTasksTable.module.css';

type SourceCellProps = {
  sourceUrl: string;
};

export function SourceCell({ sourceUrl }: SourceCellProps) {
  return (
    <a className={styles.sourceLink} href={sourceUrl} target="_blank" rel="noreferrer">
      {sourceUrl}
    </a>
  );
}
