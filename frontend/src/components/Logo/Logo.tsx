import clsx from 'clsx';
import styles from './Logo.module.css';

type LogoProps = {
  size?: 'small' | 'medium';
};

function Logo({ size = 'small' }: LogoProps) {
  return (
    <div className={clsx(styles.logo, styles[size])}>VPIKe</div>
  );
}

export default Logo;
