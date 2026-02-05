import { makeStyles } from '@material-ui/core';

const useStyles = makeStyles({
  img: {
    height: 30,
    width: 'auto',
    display: 'block',
  },
});

const LogoFull = () => {
  const classes = useStyles();

  return (
    <img className={classes.img} src="/hyundai-wia-logo.jpg" alt="HYUNDAI WIA" />
  );
};

export default LogoFull;
