import { makeStyles } from '@material-ui/core';

const useStyles = makeStyles({
  img: {
    height: 28,
    width: 'auto',
    display: 'block',
  },
});

const LogoIcon = () => {
  const classes = useStyles();

  return (
    <img className={classes.img} src="/hyundai-wia-logo.jpg" alt="WIA" />
  );
};

export default LogoIcon;
